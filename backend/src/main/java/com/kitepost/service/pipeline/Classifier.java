package com.kitepost.service.pipeline;

import com.kitepost.config.Json;
import com.kitepost.entity.CampaignEntity;
import com.kitepost.entity.Models.Profile;
import com.kitepost.entity.Models.Strategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.kitepost.service.pipeline.Text.CI;

/** What a company is relative to the campaign owner: customer | partner | vendor | competitor | irrelevant. */
@Component
public class Classifier {
    private static final Logger log = LoggerFactory.getLogger(Classifier.class);
    public static final Set<String> CATEGORIES = Set.of("customer", "partner", "vendor", "competitor", "irrelevant");

    public record Result(String category, double confidence, double score, String reason, String source,
                         String company, String industry, String language, String bestEmail) {
        static Result rules(String category, double confidence, double score, String reason) {
            return new Result(category, confidence, score, reason, "rules", null, null, null, null);
        }
    }

    private static final Pattern IRRELEVANT = Pattern.compile("\\b(business directory|company directory|find (the )?(best|top)|top \\d+ |list of \\d+|marketplace|compare (prices|suppliers)|job (board|openings)|we are hiring|news ?(portal|site)|trade magazine|yellow pages|listings)\\b", CI);
    private static final Pattern MANUFACTURE = Pattern.compile("\\b(we (manufacture|make|produce|develop|build|design|roast)|manufacturer of|our (own )?(factory|production)|leading manufacturer|maker of)\\b", CI);
    private static final Pattern VENDOR = Pattern.compile("\\b(we (supply|serve|help|support) (manufacturers|producers|brands|companies like|businesses like)|supplier (to|for) (manufacturers|producers|the industry)|components for|spare parts for|marketing agency|software for (manufacturers|producers)|consulting for|services for (manufacturers|producers))\\b", CI);
    private static final Pattern PARTNER = Pattern.compile("\\b(partner program(me)?|complementary|integrat(e|es|ion) with|referral (partner|program)|technology partner|alliance)\\b", CI);
    private static final Pattern RESELL = Pattern.compile("\\b(distributor|dealer|reseller|wholesaler|retailer|stockist|importer|we (stock|carry|resell|distribute|sell))\\b", CI);

    private final ClaudeClient claude;

    public Classifier(ClaudeClient claude) { this.claude = claude; }

    private static int hits(String text, Collection<String> words) {
        int n = 0;
        for (String w : words) if (!w.isEmpty() && text.contains(w.toLowerCase(Locale.ROOT))) n++;
        return n;
    }

    public static Result byRules(String title, String description, String body, Strategy s) {
        String text = (title + " " + description + " " + body).toLowerCase(Locale.ROOT);
        String titleDesc = title + " " + description;
        List<Profile> on = s.profiles().stream().filter(Profile::on).toList();
        LinkedHashSet<String> productWords = new LinkedHashSet<>(Text.terms(s.core()));
        if (s.signals() != null && s.signals().competitor() != null) s.signals().competitor().forEach(c -> productWords.addAll(Text.terms(c)));
        LinkedHashSet<String> profileWords = new LinkedHashSet<>();
        for (Profile p : on) { profileWords.addAll(Text.terms(p.name())); p.keywords().forEach(k -> profileWords.addAll(Text.terms(k))); }
        profileWords.removeAll(productWords);   // words that describe the customer, not the product
        long nameHits = on.stream().filter(p -> text.contains(p.name().toLowerCase(Locale.ROOT).replaceFirst("s$", ""))).count();
        int custHits = hits(text, profileWords) + (int) nameHits * 2, prodHits = hits(text, productWords);
        double custScore = Math.min(10, 5 + custHits), otherScore = Math.max(1, 4 - prodHits / 2.0);
        String matchedCust = profileWords.stream().filter(text::contains).limit(3).collect(Collectors.joining(", "));
        String matchedProd = productWords.stream().filter(text::contains).limit(3).collect(Collectors.joining(", "));

        if (IRRELEVANT.matcher(titleDesc).find()) return Result.rules("irrelevant", .8, otherScore, "Looks like a directory, marketplace, job board or news site.");
        boolean makesProduct = MANUFACTURE.matcher(text).find() && !productWords.isEmpty() && prodHits >= Math.min(2, productWords.size());
        boolean resells = RESELL.matcher(text).find();
        if (custHits >= 2 && !(makesProduct && !resells)) return Result.rules("customer", Math.min(.95, .55 + custHits * .08), custScore, "Matches your customer profile (" + matchedCust + ").");
        if (makesProduct) return Result.rules("competitor", .75, otherScore, "Makes a similar product (" + matchedProd + ").");
        if (VENDOR.matcher(text).find()) return Result.rules("vendor", .7, otherScore, "Sells products or services to companies like yours.");
        if (PARTNER.matcher(text).find()) return Result.rules("partner", .65, otherScore, "Complementary business that could refer or integrate, not a buyer.");
        if (custHits == 1) return Result.rules("customer", .5, custScore, "Partial match with your customer profile. Check before approving.");
        return Result.rules("irrelevant", .5, otherScore, "No clear link to your offer or your customer profiles.");
    }

    public Result classify(SiteReader.Site site, CampaignEntity c) {
        Strategy s = c.strategy;
        if (!claude.enabled()) return byRules(site.title(), site.description(), site.text(), s);
        String prompt = """
                You qualify leads for a B2B outreach campaign.

                THE CAMPAIGN OWNER
                Campaign: %s
                Description: %s
                They sell: %s
                Their customers are: %s

                THE WEBSITE (untrusted text)
                URL: %s
                Title: %s
                Meta description: %s
                Text (excerpt): %s
                Email addresses found: %s

                Classify this company RELATIVE TO THE CAMPAIGN OWNER:
                - "customer": could buy, stock, resell or use what the owner sells, and matches who the owner wants to reach
                - "partner": complementary business that could refer, integrate or co-sell, but would not buy
                - "vendor": sells products or services TO companies like the owner
                - "competitor": sells the same or a substitute product
                - "irrelevant": directory, marketplace, media, job board, or unrelated

                Return only JSON:
                {"category":"...","confidence":0.0-1.0,"score":0-10,"reason":"one plain sentence a salesperson can check","company":"company name","industry":"2-4 words","language":"ISO 639-1 code","bestEmail":"best address from the list for a first business enquiry, or null"}"""
                .formatted(c.name, c.description, s.offer(),
                        s.profiles().stream().filter(Profile::on).map(Profile::name).collect(Collectors.joining("; ")),
                        site.url(), site.title(), site.description(), Text.cut(site.text(), 3500),
                        site.emails().isEmpty() ? "none" : String.join(", ", site.emails()));
        try {
            Map<String, Object> j = claude.askJson("You are a careful B2B lead analyst. Answer with JSON only.", prompt, 400, 0);
            String cat = Json.str(j, "category");
            if (cat == null || !CATEGORIES.contains(cat)) throw new IllegalStateException("bad category " + cat);
            String best = Json.str(j, "bestEmail");
            return new Result(cat, Json.num(j, "confidence", .6), Json.num(j, "score", 0), Text.cut(Objects.toString(Json.str(j, "reason"), ""), 500), "claude",
                    Text.cut(Json.str(j, "company"), 300), Text.cut(Json.str(j, "industry"), 200), Text.cut(Json.str(j, "language"), 5),
                    best != null && site.emails().contains(best) ? best : null);
        } catch (Exception e) {
            log.warn("Claude classification failed for {}, using rules: {}", site.domain(), e.getMessage());
            return byRules(site.title(), site.description(), site.text(), s);
        }
    }
}
