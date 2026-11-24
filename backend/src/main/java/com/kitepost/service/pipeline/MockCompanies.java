package com.kitepost.service.pipeline;

import com.kitepost.entity.CampaignEntity;
import com.kitepost.entity.Models.Profile;
import com.kitepost.entity.Models.Query;
import com.kitepost.service.ReferenceData;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Demo mode: fictional companies on .example domains, built from the campaign's customer profiles. */
@Component
public class MockCompanies {
    private final ReferenceData ref;
    private final Map<String, SiteReader.Site> sites = new ConcurrentHashMap<>();

    public MockCompanies(ReferenceData ref) { this.ref = ref; }

    public static boolean isMockDomain(String d) { return d != null && d.endsWith(".example"); }

    private static <T> T pick(List<T> list) { return list.get(ThreadLocalRandom.current().nextInt(list.size())); }
    private static double rnd() { return ThreadLocalRandom.current().nextDouble(); }

    static String slug(String s) {
        String t = s.toLowerCase(Locale.ROOT).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss").replace("ł", "l");
        t = Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace("&", "and");
        return t.replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private List<String> names(Map<String, List<String>> byCountry, Map<String, List<String>> byLang, String country, String lang) {
        if (byCountry != null && byCountry.containsKey(country)) return byCountry.get(country);
        return byLang.getOrDefault(lang, byLang.get("en"));
    }

    public List<SearchService.Result> search(Query q, CampaignEntity c) throws InterruptedException {
        Thread.sleep(400 + (long) (rnd() * 600));
        int n = 4 + ThreadLocalRandom.current().nextInt(4);
        List<SearchService.Result> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(build(q, c));
        return out;
    }

    public SiteReader.Site site(String domain) throws IOException, InterruptedException {
        Thread.sleep(300 + (long) (rnd() * 900));
        SiteReader.Site s = sites.get(domain);
        if (s == null) throw new IOException("demo site no longer in memory");
        return s;
    }

    private SearchService.Result build(Query q, CampaignEntity c) {
        double r = rnd();
        String cat = r < .46 ? "customer" : r < .62 ? "competitor" : r < .74 ? "vendor" : r < .84 ? "partner" : "irrelevant";
        ReferenceData.Country ct = ref.country(q.country()).orElseThrow();
        ReferenceData.MockNames mn = ref.mockNames();
        String sur = pick(names(mn.surnamesByCountry(), mn.surnames(), q.country(), ct.lang()));
        String suf = pick(names(mn.suffixesByCountry(), mn.suffixes(), q.country(), ct.lang()));
        String city = q.location() != null && !q.location().equals(ct.name()) ? q.location()
                : pick(mn.cities().getOrDefault(q.country(), List.of(ct.name())));
        String core = Text.blank(c.strategy.core()) ? "your product" : c.strategy.core();
        Profile profile = pick(c.strategy.profiles().stream().filter(Profile::on).toList());
        String pname = profile.name().toLowerCase(Locale.ROOT);
        String kw0 = profile.keywords().isEmpty() ? pname : profile.keywords().get(0);
        String company = sur + " " + suf, title, description, text;
        switch (cat) {
            case "customer" -> {
                title = company + " | " + profile.name() + " in " + city;
                description = company + " is one of " + ct.name() + "'s independent " + pname + ", serving customers from " + city + ".";
                text = "About us. " + company + " is an established " + pname.replaceFirst("s$", "") + " based in " + city + ", " + ct.name() + ". "
                        + kw0 + ": that's what our customers know us for. We have 40 staff and serve more than 600 business customers. "
                        + "We are always looking for new products and suppliers to add to our range, and our purchasing team reviews new offers every quarter. Contact our team for enquiries.";
            }
            case "competitor" -> {
                String last = core.substring(core.lastIndexOf(' ') + 1);
                company = sur + " " + Text.capitalize(last) + " " + suf;
                title = company + " | Manufacturer of " + core;
                description = "Leading manufacturer of " + core + " since 1998.";
                text = company + " is a leading manufacturer of " + core + ". We design and produce " + core + " in our own factory in " + city
                        + ". Our engineers develop every model in-house and we ship to 30 countries.";
            }
            case "vendor" -> {
                title = company + " | Components and services for manufacturers";
                description = "We supply manufacturers with parts, tooling and engineering services.";
                text = company + " supplies components, spare parts and engineering services to manufacturers. We help producers of " + core
                        + " shorten lead times and reduce costs. ISO 9001 certified, based in " + city + ".";
            }
            case "partner" -> {
                title = company + " | Software and consulting";
                description = "Complementary services with a partner programme for equipment brands.";
                text = company + " offers training, installation and maintenance planning. Our partner programme connects equipment brands with end users, "
                        + "and our platform integrates with leading systems. Based in " + city + ".";
            }
            default -> {
                company = ct.name() + " Business Finder";
                title = "Top 50 " + kw0 + " in " + ct.name() + " | Business directory";
                description = "Find the best " + kw0 + " near you. Compare ratings and contact details.";
                text = "Business directory listing " + kw0 + " companies in " + ct.name() + ". Sponsored listings. Add your company for free.";
            }
        }
        String domain = slug(company.replaceAll("\\b(GmbH|S\\.?L\\.?|B\\.?V\\.?|SARL|S\\.r\\.l\\.|Lda\\.|AB|ApS|A/S|AS|Oy|KK|Co\\.,? Ltd\\.|Sp\\. z o\\.o\\.|& Co\\. KG|Pvt\\. Ltd\\.)(?=\\s|$)", "")) + ".example";
        boolean hasEmail = cat.equals("irrelevant") ? rnd() < .4 : rnd() < .86;
        List<String> emails = hasEmail ? List.of(pick(List.of("info", "sales", "hello", "contact")) + "@" + domain) : List.of();
        String phone = "+1 555 01" + (10 + (int) (rnd() * 90)) + " " + (1000 + (int) (rnd() * 9000));
        SiteReader.Site site = new SiteReader.Site("https://" + domain + "/", domain, title, description, ct.lang(),
                text + (hasEmail ? " Email: " + emails.get(0) : ""), emails, List.of("https://" + domain + "/", "https://" + domain + "/contact"), List.of(phone));
        sites.put(domain, site);
        if (sites.size() > 20_000) sites.clear();
        return new SearchService.Result(site.url(), title, description, phone);
    }
}
