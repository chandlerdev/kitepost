package com.kitepost.service.pipeline;

import com.kitepost.config.Json;
import com.kitepost.entity.Models.Profile;
import com.kitepost.entity.Models.Signals;
import com.kitepost.entity.Models.Strategy;
import com.kitepost.service.ReferenceData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.kitepost.service.pipeline.Text.CI;

/** Campaign name + description → customer profiles and search keywords (Claude, or rules without a key). */
@Component
public class StrategyBuilder {
    private static final Logger log = LoggerFactory.getLogger(StrategyBuilder.class);
    private static final Pattern RESELLER = Pattern.compile("(distributor|dealer|reseller|wholesaler|retailer|integrator|stockist|agent|importer)s?\\b", CI);
    private static final Pattern OFFER = Pattern.compile("\\bwe (?:make|sell|build|produce|offer|manufacture|provide|develop|create|roast|brew|design)\\s+(?:the\\s+|an?\\s+|our\\s+)?([^.;\\n]{3,100})", CI);
    private static final List<Pattern> AUDIENCE = List.of(
            Pattern.compile("\\b(?:looking for|searching for|want(?:ing)? to reach|want to find|targeting|target customers are|our customers are|selling to|sell to|aimed at)\\s+([^.]{3,160})", CI),
            Pattern.compile("\\bwe want\\s+([^.]{3,160})", CI),
            Pattern.compile("\\b(?:for)\\s+([^.]{3,160})", CI));
    private static final Pattern SPLIT = Pattern.compile(",|\\band\\b|\\bor\\b|\\bas well as\\b", CI);
    private static final Pattern CONTEXT = Pattern.compile("\\b(?:sell|supply|distribute|stock|carry)s?\\s+(?!our\\b|your\\b|it\\b|them\\b)([a-z][a-z &-]{2,40}?)(?=\\s+(?:to|for|in|across|and)\\b|[.,;]|$)", CI);

    public record Input(String name, String description, String goal, List<String> countries) {}

    private final ClaudeClient claude;
    private final ReferenceData ref;

    public StrategyBuilder(ClaudeClient claude, ReferenceData ref) {
        this.claude = claude;
        this.ref = ref;
    }

    public static String offerFrom(String desc, String name) {
        Matcher m = OFFER.matcher(desc);
        return (m.find() ? m.group(1) : name.split(":")[0]).trim();
    }

    /** "the LX-200, a print & apply labeller" → "print & apply labeller"; "ChairTime, online scheduling ..." → "online scheduling and reminder". */
    public static String coreTerm(String offer) {
        String[] parts = offer.split(", ");
        String first = parts[0];
        boolean brand = parts.length > 1 && first.split(" ").length <= 3
                && (Pattern.compile("\\d|^[A-Z0-9-]+$").matcher(first).find() || Pattern.compile("^(the |an? )?[A-Z]").matcher(first).find());
        String s = brand ? String.join(", ", Arrays.copyOfRange(parts, 1, parts.length)) : first;
        s = s.replaceFirst("(?i)^(an?|the|our)\\s+", "").replaceFirst("(?i)\\s(for|that|which|with|roasted|made|produced|based|in|to|from|who)\\s.*$", "");
        String[] words = s.trim().split("\\s+");
        return String.join(" ", Arrays.copyOfRange(words, 0, Math.min(4, words.length))).toLowerCase(Locale.ROOT);
    }

    static List<String> audienceFrom(String desc) {
        Matcher m = null;
        for (Pattern p : AUDIENCE) { m = p.matcher(desc); if (m.find()) break; m = null; }
        if (m == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String piece : SPLIT.split(m.group(1))) {
            String p = piece.replaceFirst("(?i)\\b(who|that|which|with|to|so|because|in order)\\b.*$", "")
                    .replaceFirst("(?i)^(the|other|more|new|all)\\s+", "").trim();
            if (p.length() > 2 && p.split("\\s+").length <= 5 && !Pattern.compile("\\b(them|it|us|our|their|you|we)\\b", CI).matcher(p).find()) out.add(p);
            if (out.size() == 4) break;
        }
        return out;
    }

    public Strategy byRules(Input in) {
        String offer = offerFrom(in.description(), in.name()), core = coreTerm(offer);
        List<String> pieces = audienceFrom(in.description());
        if (pieces.isEmpty()) pieces = "Distributors".equals(in.goal()) || "Partnerships".equals(in.goal())
                ? List.of("distributors", "resellers") : List.of("companies that buy " + core, "businesses using " + core);
        Matcher cm = CONTEXT.matcher(in.description());
        String ctx = cm.find() ? cm.group(1).trim().toLowerCase(Locale.ROOT) : "";
        String lastCoreWord = core.substring(core.lastIndexOf(' ') + 1);
        List<Profile> profiles = new ArrayList<>();
        for (int i = 0; i < pieces.size(); i++) {
            String p = pieces.get(i);
            LinkedHashSet<String> kw = new LinkedHashSet<>();
            if (RESELLER.matcher(p).find()) { kw.add(core + " " + p); kw.add(ctx.isEmpty() ? p + " " + lastCoreWord : ctx + " " + p); }
            else kw.add(p);
            profiles.add(new Profile("p" + i, Text.capitalize(p), "From your description: you want to reach " + p.toLowerCase(Locale.ROOT) + ".", List.copyOf(kw), true));
        }
        return new Strategy(offer, core, "rules", profiles, Map.of(),
                new Signals(Text.terms(core), List.of("supplier to manufacturers", "components", "agency", "software for", "consulting"),
                        List.of("partner program", "integration", "complementary", "referral")));
    }

    public Strategy build(Input in) {
        if (!claude.enabled()) return byRules(in);
        List<String> countries = in.countries() == null ? List.of() : in.countries();
        Set<String> langs = countries.stream().map(c -> ref.country(c).map(ReferenceData.Country::lang).orElse("en"))
                .filter(l -> !"en".equals(l)).collect(Collectors.toCollection(LinkedHashSet::new));
        String local = langs.stream().map(l -> "\"" + l + "\": [\"4-6 of the best phrases translated into " + ref.languageName(l) + "\"]").collect(Collectors.joining(", "));
        String prompt = """
                A company is setting up a B2B outreach campaign.

                Campaign name: %s
                Goal: %s
                Description (written by the company): %s
                Target countries: %s

                Work out who the company's CUSTOMERS are: businesses that would buy, stock, resell or use what this company offers, matching who the description says they want to reach. Respect any exclusions in the description.
                Then write web search keywords that find those customers' own websites (not directories, not news).

                Return only JSON:
                {"offer":"one short phrase: what the company sells","core":"2-4 word generic product category",
                 "profiles":[{"name":"customer type","why":"one sentence","keywords":["3-5 short search phrases in English"]}],
                 "localKeywords":{%s},
                 "signals":{"competitor":["words marking a company selling the same or a substitute"],"vendor":["words marking a company selling TO this company"],"partner":["words marking a complementary business that is not a buyer"]}}
                Use 2-4 profiles.""".formatted(in.name(), in.goal(), in.description(),
                countries.stream().map(ref::countryName).collect(Collectors.joining(", ")), local);
        try {
            Map<String, Object> j = claude.askJson("You are a precise B2B market researcher. Answer with JSON only.", prompt, 1500, 0.2);
            List<Profile> profiles = new ArrayList<>();
            Object raw = j.get("profiles");
            if (raw instanceof List<?> list) {
                int i = 0;
                for (Object o : list) {
                    if (!(o instanceof Map<?, ?> pm) || i >= 4) continue;
                    @SuppressWarnings("unchecked") Map<String, Object> p = (Map<String, Object>) pm;
                    List<String> kws = Json.strings(p, "keywords");
                    profiles.add(new Profile("p" + i++, Text.cut(Json.str(p, "name"), 120), Text.cut(Objects.toString(Json.str(p, "why"), ""), 300),
                            kws.subList(0, Math.min(5, kws.size())).stream().map(k -> Text.cut(k, 120)).toList(), true));
                }
            }
            if (profiles.isEmpty()) throw new IllegalStateException("no profiles");
            Map<String, List<String>> localKw = new LinkedHashMap<>();
            Json.obj(j, "localKeywords").forEach((k, v) -> { if (v instanceof List<?> l) localKw.put(k, l.stream().map(String::valueOf).limit(6).toList()); });
            Map<String, Object> sig = Json.obj(j, "signals");
            String offer = Objects.requireNonNullElse(Json.str(j, "offer"), offerFrom(in.description(), in.name()));
            return new Strategy(Text.cut(offer, 200), Text.cut(Objects.requireNonNullElse(Json.str(j, "core"), ""), 80), "claude", profiles, localKw,
                    new Signals(Json.strings(sig, "competitor"), Json.strings(sig, "vendor"), Json.strings(sig, "partner")));
        } catch (Exception e) {
            log.warn("Claude strategy failed, using rules: {}", e.getMessage());
            return byRules(in);
        }
    }
}
