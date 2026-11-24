package com.kitepost.service;

import com.kitepost.config.AppProperties;
import com.kitepost.entity.CampaignEntity;
import com.kitepost.entity.Ids;
import com.kitepost.entity.LeadEntity;
import com.kitepost.entity.Models.*;
import com.kitepost.service.pipeline.*;
import com.kitepost.repository.CampaignRepository;
import com.kitepost.repository.LeadRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Runs a campaign in the background: search country by country (and state by state),
 * read each website, classify it, draft an email for customers. Nothing is sent here.
 */
@Service
public class CampaignRunner {
    private static final Logger log = LoggerFactory.getLogger(CampaignRunner.class);

    private final CampaignRepository campaigns;
    private final LeadRepository leads;
    private final Entities entities;
    private final ActivityService activity;
    private final StatsService stats;
    private final SuppressionService suppression;
    private final ReferenceData ref;
    private final SearchService search;
    private final SiteReader reader;
    private final Classifier classifier;
    private final int concurrency;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, AtomicBoolean> running = new ConcurrentHashMap<>();

    public CampaignRunner(CampaignRepository campaigns, LeadRepository leads, Entities entities, ActivityService activity,
                          StatsService stats, SuppressionService suppression, ReferenceData ref, SearchService search,
                          SiteReader reader, Classifier classifier, AppProperties props) {
        this.campaigns = campaigns;
        this.leads = leads;
        this.entities = entities;
        this.activity = activity;
        this.stats = stats;
        this.suppression = suppression;
        this.ref = ref;
        this.search = search;
        this.reader = reader;
        this.classifier = classifier;
        this.concurrency = Math.max(1, props.fetch().concurrency());
    }

    @PreDestroy
    void shutdown() { executor.shutdownNow(); }

    public boolean isRunningHere(String id) { return running.containsKey(id); }

    public List<Query> buildQueries(CampaignEntity c) {
        List<String> english = c.strategy.profiles().stream().filter(Profile::on).flatMap(p -> p.keywords().stream()).toList();
        List<Query> out = new ArrayList<>();
        for (String code : c.countries) {
            Optional<ReferenceData.Country> oc = ref.country(code);
            if (oc.isEmpty()) continue;
            ReferenceData.Country ct = oc.get();
            List<String> regions = c.regions != null && c.regions.containsKey(code) && !c.regions.get(code).isEmpty() ? c.regions.get(code) : List.of(ct.name());
            List<String> local = !"en".equals(ct.lang()) && c.strategy.localKeywords() != null ? c.strategy.localKeywords().getOrDefault(ct.lang(), List.of()) : List.of();
            LinkedHashSet<String> kws = new LinkedHashSet<>();
            if (!local.isEmpty()) { kws.addAll(local); kws.addAll(english.subList(0, Math.min(2, english.size()))); }
            else kws.addAll(english);
            for (String loc : regions) for (String kw : kws) {
                String suffix = loc.equals(ct.name()) ? "" : " " + ("US".equals(code) ? "USA" : ct.name());
                out.add(new Query(kw + " " + loc + suffix, code, ct.gl(), loc));
            }
        }
        return out;
    }

    private static final Pattern TITLE_SPLIT = Pattern.compile("\\s[|–—-]\\s|\\s·\\s");

    static String companyFrom(String title, String domain) {
        String t = title == null ? "" : TITLE_SPLIT.split(title)[0].trim();
        if (!t.isEmpty() && t.length() <= 60) return t;
        String base = domain.split("\\.")[0].replace('-', ' ');
        StringBuilder sb = new StringBuilder();
        for (String w : base.split(" ")) if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return sb.toString().trim();
    }

    /** Sets review / no_email / suppressed. The email itself comes from the template you choose in Review. */
    public void prepareForReview(CampaignEntity c, LeadEntity l) {
        if (Text.blank(l.email)) { l.status = "no_email"; return; }
        if (suppression.isSuppressed(l.email)) { l.status = "suppressed"; return; }
        l.status = "review";
    }

    void processLead(CampaignEntity c, LeadEntity lead) {
        final SiteReader.Site site;
        try {
            site = reader.read(lead.url);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (Exception e) {
            entities.patchLead(lead.id, l -> { l.status = "skipped"; l.error = Text.cut("Could not read the website: " + e.getMessage(), 1000); l.updatedAt = Ids.now(); });
            activity.add(c.id, "skip", "Could not read " + lead.domain, lead.id);
            return;
        }
        Classifier.Result cls = classifier.classify(site, c);
        Optional<LeadEntity> saved = entities.patchLead(lead.id, l -> {
            l.title = Text.cut(site.title(), 500);
            l.siteSummary = Text.cut((Text.blank(site.description()) ? "" : site.description() + " ") + Text.cut(site.text(), 900), 2000);
            l.emails = site.emails();
            l.pages = site.pages();
            if (Text.blank(l.phone) && !site.phones().isEmpty()) l.phone = Text.cut(site.phones().get(0), 60);
            l.company = Text.cut(Text.blank(cls.company()) ? companyFrom(site.title(), l.domain) : cls.company(), 300);
            String lang = cls.language() != null && cls.language().length() == 2 ? cls.language()
                    : !Text.blank(site.lang()) ? site.lang() : ref.country(l.country).map(ReferenceData.Country::lang).orElse("en");
            l.lang = Text.cut(lang, 5);
            l.category = cls.category();
            l.confidence = cls.confidence();
            l.score = Math.round(cls.score() * 10) / 10.0;
            l.reason = cls.reason();
            l.categorySource = cls.source();
            l.industry = cls.industry();
            l.email = cls.bestEmail() != null ? cls.bestEmail() : site.emails().isEmpty() ? null : site.emails().get(0);
            if ("customer".equals(l.category)) prepareForReview(c, l);
            else l.status = "classified";
            l.updatedAt = Ids.now();
        });
        saved.ifPresent(l -> {
            String name = l.company.replaceFirst("\\.$", "");
            if ("customer".equals(l.category)) activity.add(c.id, "customer", "review".equals(l.status)
                    ? "Customer found: " + name + ". Choose a template in Review" : "Customer found: " + name + ", but no email address on the site", l.id);
            else activity.add(c.id, l.category, "Marked as " + ("irrelevant".equals(l.category) ? "not relevant" : l.category) + ": " + name, l.id);
        });
    }

    private void pool(List<LeadEntity> items, CampaignEntity c, AtomicBoolean stop) throws InterruptedException {
        Semaphore slots = new Semaphore(concurrency);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            LeadEntity l = items.get(i);
            if (stop.get()) break;
            slots.acquire();
            if (limitsReached(c)) {                  // stop as soon as the target is met, not at the end of the batch
                slots.release();
                for (LeadEntity rest : items.subList(i, items.size())) leads.deleteById(rest.id);   // not analysed, so not counted
                break;
            }
            futures.add(executor.submit(() -> { try { processLead(c, l); } finally { slots.release(); } }));
        }
        for (Future<?> f : futures) {
            try { f.get(); } catch (ExecutionException e) { log.warn("Lead processing failed: {}", e.getCause().toString()); }
        }
    }

    private boolean limitsReached(CampaignEntity c) {
        Stats st = stats.of(c.id);
        return st.customer() >= c.targetCustomers;
    }

    private void loop(String id, AtomicBoolean stop) throws InterruptedException {
        CampaignEntity c = campaigns.findById(id).orElse(null);
        if (c == null) return;
        pool(leads.findByCampaignIdAndStatus(id, "analyzing"), c, stop);   // finish work interrupted by a restart
        int failures = 0;
        while (!stop.get()) {
            c = campaigns.findById(id).orElse(null);                         // always work from the latest copy
            if (c == null || c.queryIndex >= c.queries.size()) break;
            Stats st = stats.of(id);
            if (st.customer() >= c.targetCustomers) { activity.add(id, "info", "Reached your target of " + c.targetCustomers + " customers"); break; }
            if (st.found() >= c.maxSites) { activity.add(id, "info", "Reached the limit of " + c.maxSites + " websites"); break; }
            Query q = c.queries.get(c.queryIndex);
            entities.patchCampaign(id, x -> x.current = new CurrentSearch(q.country(), q.location(), q.q()));
            activity.add(id, "search", "Searching \"" + q.q() + "\"");
            List<SearchService.Result> results;
            try {
                results = search.search(q, c);
                failures = 0;
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                failures++;
                activity.add(id, "error", "Search failed: " + Text.cut(e.getMessage(), 300));
                if (failures >= 3) {
                    String msg = Text.cut("Search keeps failing: " + e.getMessage(), 1000);
                    entities.patchCampaign(id, x -> { x.status = "paused"; x.error = msg; });
                    return;
                }
                Thread.sleep(3000);
                continue;
            }
            List<LeadEntity> fresh = new ArrayList<>();
            int room = Math.max(0, c.maxSites - st.found());
            for (SearchService.Result r : results) {
                if (fresh.size() >= room) break;       // never save more sites than the limit
                String domain = SiteReader.domainOf(r.url());
                if (domain.isEmpty() || leads.existsByCampaignIdAndDomain(id, domain)) continue;
                LeadEntity l = new LeadEntity();
                l.id = Ids.next("l");
                l.campaignId = id;
                l.domain = Text.cut(domain, 255);
                l.url = Text.cut(r.url(), 2000);
                l.company = Text.cut(companyFrom(r.title(), domain), 300);
                l.country = q.country();
                l.location = Text.cut(q.location(), 120);
                l.query = Text.cut(q.q(), 500);
                l.snippet = Text.cut(r.snippet(), 2000);
                l.phone = Text.blank(r.phone()) ? null : Text.cut(r.phone().trim(), 60);   // the Maps listing number; the website may fill it in later
                l.createdAt = l.updatedAt = Ids.now();
                boolean blocked = ref.mockNames().blockedDomains().stream().anyMatch(b -> domain.equals(b) || domain.endsWith("." + b));
                if (blocked) {
                    l.status = "classified"; l.category = "irrelevant"; l.reason = "Social network, directory or marketplace.";
                    l.categorySource = "rules"; l.confidence = 1.0; l.score = 0.0;
                } else l.status = "analyzing";
                try { leads.save(l); } catch (DataIntegrityViolationException dup) { continue; } // same domain saved meanwhile
                if (!blocked) fresh.add(l);
            }
            pool(fresh, c, stop);
            int done = c.queryIndex + 1;
            entities.patchCampaign(id, x -> x.queryIndex = Math.max(x.queryIndex, done));
        }
        if (!stop.get()) {
            entities.patchCampaign(id, x -> { x.status = "done"; x.current = null; });
            activity.add(id, "info", "Search finished. Review the drafted emails to send them.");
        }
    }

    public void start(String id) {
        if (running.containsKey(id)) return;
        CampaignEntity c = campaigns.findById(id).orElse(null);
        if (c == null) return;
        boolean first = c.startedAt == null;
        Optional<CampaignEntity> saved = entities.patchCampaign(id, x -> {
            if (x.queries == null || x.queries.isEmpty()) { x.queries = buildQueries(x); x.queryIndex = 0; }
            if ("done".equals(x.status) && x.queryIndex >= x.queries.size()) return;
            x.status = "running";
            x.error = null;
            if (x.startedAt == null) x.startedAt = Ids.now();
        });
        if (saved.isEmpty() || !"running".equals(saved.get().status)) return;
        activity.add(id, "info", first ? "Campaign started. Searching " + ref.countryName(saved.get().countries.get(0)) + " first" : "Campaign resumed");
        AtomicBoolean stop = new AtomicBoolean(false);
        if (running.putIfAbsent(id, stop) != null) return;
        executor.submit(() -> {
            try {
                loop(id, stop);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Campaign {} stopped with an error", id, e);
                String msg = Text.cut(e.getMessage() == null ? e.toString() : e.getMessage(), 1000);
                try { entities.patchCampaign(id, x -> { x.status = "paused"; x.error = msg; }); } catch (Exception ignored) { }
            } finally {
                running.remove(id);
            }
        });
    }

    public void pause(String id) {
        AtomicBoolean stop = running.remove(id);
        if (stop != null) stop.set(true);
        entities.patchCampaign(id, x -> x.status = "paused");
        activity.add(id, "info", "Campaign paused");
    }

    /** On startup: carry on with campaigns that were searching when the server stopped. */
    public void resumeAll() {
        for (CampaignEntity c : campaigns.findByStatus("running")) {
            if (running.containsKey(c.id)) continue;
            entities.patchCampaign(c.id, x -> x.status = "paused");
            start(c.id);
        }
    }
}
