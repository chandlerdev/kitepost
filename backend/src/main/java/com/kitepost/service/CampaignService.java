package com.kitepost.service;

import com.kitepost.exception.ApiException;
import com.kitepost.dto.Dtos.*;
import com.kitepost.entity.CampaignEntity;
import com.kitepost.entity.Ids;
import com.kitepost.entity.Models.*;
import com.kitepost.service.pipeline.StrategyBuilder;
import com.kitepost.service.pipeline.Text;
import com.kitepost.repository.CampaignRepository;
import com.kitepost.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CampaignService {
    private static final Set<String> TONES = Set.of("friendly", "formal", "direct");

    private final CampaignRepository campaigns;
    private final Entities entities;
    private final StatsService stats;
    private final ActivityService activity;
    private final CampaignRunner runner;
    private final StrategyBuilder strategyBuilder;
    private final ReferenceData ref;
    private final AuditService audit;

    public CampaignService(CampaignRepository campaigns, Entities entities, StatsService stats, ActivityService activity,
                           CampaignRunner runner, StrategyBuilder strategyBuilder, ReferenceData ref, AuditService audit) {
        this.campaigns = campaigns;
        this.entities = entities;
        this.stats = stats;
        this.activity = activity;
        this.runner = runner;
        this.strategyBuilder = strategyBuilder;
        this.ref = ref;
        this.audit = audit;
    }

    CampaignEntity mustGet(String id) {
        return campaigns.findById(id).orElseThrow(() -> ApiException.notFound("Campaign not found"));
    }

    public Strategy strategy(StrategyRequest b) {
        List<String> countries = b.countries() == null ? List.of() : b.countries().stream().filter(c -> ref.country(c).isPresent()).toList();
        return strategyBuilder.build(new StrategyBuilder.Input(b.name().trim(), b.description().trim(), Objects.requireNonNullElse(b.goal(), "Sales"), countries));
    }

    public List<CampaignOut> list() {
        Map<String, Stats> all = stats.all();
        return campaigns.findAllByOrderByCreatedAtDesc().stream()
                .map(c -> CampaignOut.of(c, all.getOrDefault(c.id, StatsService.empty()), null)).toList();
    }

    public CampaignOut detail(String id) {
        CampaignEntity c = mustGet(id);
        return CampaignOut.of(c, stats.of(id), activity.recent(id, 60).stream().map(ActivityOut::of).toList());
    }

    /** Keeps only well-formed, size-limited profiles from the client. */
    static Strategy sanitize(Strategy s) {
        List<Profile> profiles = new ArrayList<>();
        int i = 0;
        for (Profile p : s.profiles() == null ? List.<Profile>of() : s.profiles()) {
            if (p == null || Text.blank(p.name()) || profiles.size() >= 8) continue;
            List<String> kws = (p.keywords() == null ? List.<String>of() : p.keywords()).stream()
                    .filter(k -> !Text.blank(k)).map(k -> Text.cut(k.trim(), 120)).distinct().limit(10).toList();
            profiles.add(new Profile("p" + i++, Text.cut(p.name().trim(), 120), Text.cut(Objects.toString(p.why(), ""), 300), kws, p.on()));
        }
        Map<String, List<String>> local = new LinkedHashMap<>();
        if (s.localKeywords() != null) s.localKeywords().forEach((k, v) -> {
            if (k != null && k.length() <= 5 && v != null) local.put(k, v.stream().filter(Objects::nonNull).map(x -> Text.cut(x, 120)).limit(10).toList());
        });
        Signals sig = s.signals() == null ? Signals.empty() : s.signals();
        return new Strategy(Text.cut(Objects.toString(s.offer(), ""), 200), Text.cut(Objects.toString(s.core(), ""), 80),
                "claude".equals(s.source()) ? "claude" : "rules", profiles, local, sig);
    }

    public CampaignOut create(NewCampaignRequest b) {
        List<String> countries = b.countries().stream().distinct().toList();
        ApiException.require(countries.stream().allMatch(c -> ref.country(c).isPresent()), "Pick countries from the list.");
        Strategy strategy = sanitize(b.strategy());
        ApiException.require(strategy.profiles().stream().anyMatch(p -> p.on() && !p.keywords().isEmpty()), "Keep at least one customer type with a search phrase.");
        Sender sender = b.sender().toSender();
        ApiException.require(!countries.contains("US") || !sender.address().isBlank(), "Add your postal address. US law (CAN-SPAM) requires it in commercial emails.");

        Map<String, List<String>> regions = new LinkedHashMap<>();
        if (b.regions() != null) b.regions().forEach((code, list) -> {
            ReferenceData.Region allowed = ref.reference().regions().get(code);
            if (countries.contains(code) && allowed != null && list != null) {
                List<String> ok = list.stream().filter(allowed.list()::contains).distinct().toList();
                if (!ok.isEmpty()) regions.put(code, ok);
            }
        });
        String description = b.description().trim();
        CampaignEntity c = new CampaignEntity();
        c.id = Ids.next("c");
        c.name = b.name().trim();
        c.description = description;
        c.goal = Text.cut(Objects.requireNonNullElse(b.goal(), "Sales"), 40);
        c.countries = countries;
        c.regions = regions;
        c.strategy = strategy;
        String[] sentences = description.split("(?<=[.!?])\\s+");
        c.pitch = String.join(" ", Arrays.copyOfRange(sentences, 0, Math.min(2, sentences.length)));
        c.sender = sender;
        c.tone = TONES.contains(b.tone()) ? b.tone() : "friendly";
        c.emailLanguage = "english".equals(b.emailLanguage()) ? "english" : "local";
        c.targetCustomers = b.targetCustomers() == null ? 25 : b.targetCustomers();
        c.maxSites = b.maxSites() == null ? 250 : b.maxSites();
        c.status = "draft";
        c.queries = new ArrayList<>();
        c.queryIndex = 0;
        c.sending = true;
        c.createdBy = CurrentUser.id().orElse(null);
        c.createdAt = Ids.now();
        campaigns.save(c);
        activity.add(c.id, "info", "Campaign created");
        audit.record("campaign.create", c.id, c.name);
        if (!Boolean.FALSE.equals(b.start())) runner.start(c.id);
        return detail(c.id);
    }

    public CampaignOut patch(String id, CampaignPatch b) {
        mustGet(id);
        if (b.tone() != null) ApiException.require(TONES.contains(b.tone()), "Unknown tone.");
        entities.patchCampaign(id, x -> {
            if (!Text.blank(b.name())) x.name = b.name().trim();
            if (b.tone() != null) x.tone = b.tone();
            if (b.sending() != null) x.sending = b.sending();
            if (b.targetCustomers() != null) x.targetCustomers = b.targetCustomers();
            if (b.sender() != null) x.sender = b.sender().toSender();
        });
        if (b.sending() != null) {
            activity.add(id, "info", b.sending() ? "Sending resumed" : "Sending paused");
            audit.record(b.sending() ? "campaign.sending.resume" : "campaign.sending.pause", id, null);
        }
        return detail(id);
    }

    public CampaignOut action(String id, String action, MoreRequest body) {
        mustGet(id);
        switch (action) {
            case "start" -> runner.start(id);
            case "pause" -> runner.pause(id);
            case "more" -> {
                int add = body == null || body.add() == null ? 25 : body.add();
                entities.patchCampaign(id, x -> {
                    x.targetCustomers = Math.min(1000, x.targetCustomers + add);
                    if ("done".equals(x.status)) {
                        if (x.queryIndex >= x.queries.size()) {   // demo search: go through the places again for new companies
                            List<Query> more = new ArrayList<>(x.queries);
                            more.addAll(runner.buildQueries(x));
                            x.queries = more;
                        }
                        x.status = "paused";
                    }
                });
                runner.start(id);
            }
            default -> throw ApiException.notFound("Unknown action \"" + action + "\"");
        }
        audit.record("campaign." + action, id, null);
        return detail(id);
    }
}
