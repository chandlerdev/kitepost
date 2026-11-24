package com.kitepost.service.pipeline;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pulls title, description, language, visible text, links and emails out of HTML. */
public final class HtmlParser {
    private HtmlParser() {}

    public record Link(String href, String label) {}
    public record Parsed(String title, String description, String lang, String text, List<Link> links, List<String> mailto, List<String> tel) {}

    private static final int F = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>", F);
    private static final Pattern DESC1 = Pattern.compile("<meta[^>]+name=[\"']description[\"'][^>]*content=[\"']([^\"']*)[\"']", F);
    private static final Pattern DESC2 = Pattern.compile("<meta[^>]+content=[\"']([^\"']*)[\"'][^>]*name=[\"']description[\"']", F);
    private static final Pattern LANG = Pattern.compile("<html[^>]*\\slang=[\"']?([a-z]{2})", F);
    private static final Pattern NOISE = Pattern.compile("<(script|style|noscript|svg|template)\\b.*?</\\1>", F);
    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", F);
    private static final Pattern TAG = Pattern.compile("<[^>]+>", F);
    private static final Pattern LINK = Pattern.compile("<a[^>]+href=[\"']([^\"'#]+)[\"'][^>]*>(.*?)</a>", F);
    private static final Pattern MAILTO = Pattern.compile("mailto:([^\"'?\\s>]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEL = Pattern.compile("href=[\"']tel:([^\"'?>]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENTITY = Pattern.compile("&(#x?[0-9a-f]+|[a-z]+);", Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> ENT = Map.ofEntries(
            Map.entry("amp", "&"), Map.entry("lt", "<"), Map.entry("gt", ">"), Map.entry("quot", "\""), Map.entry("apos", "'"),
            Map.entry("nbsp", " "), Map.entry("auml", "ä"), Map.entry("ouml", "ö"), Map.entry("uuml", "ü"), Map.entry("Auml", "Ä"),
            Map.entry("Ouml", "Ö"), Map.entry("Uuml", "Ü"), Map.entry("szlig", "ß"), Map.entry("eacute", "é"), Map.entry("egrave", "è"),
            Map.entry("agrave", "à"), Map.entry("ccedil", "ç"));

    static String decode(String s) {
        Matcher m = ENTITY.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String e = m.group(1), rep;
            if (e.startsWith("#")) {
                try {
                    int cp = e.length() > 1 && (e.charAt(1) == 'x' || e.charAt(1) == 'X') ? Integer.parseInt(e.substring(2), 16) : Integer.parseInt(e.substring(1));
                    rep = Character.isValidCodePoint(cp) ? new String(Character.toChars(cp)) : m.group();
                } catch (NumberFormatException ex) { rep = m.group(); }
            } else rep = ENT.getOrDefault(e, m.group());
            m.appendReplacement(out, Matcher.quoteReplacement(rep));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String first(Pattern p, String html) { Matcher m = p.matcher(html); return m.find() ? m.group(1) : ""; }

    public static Parsed parse(String html, URI base) {
        String title = decode(first(TITLE, html)).replaceAll("\\s+", " ").trim();
        String desc = first(DESC1, html);
        if (desc.isEmpty()) desc = first(DESC2, html);
        String lang = first(LANG, html).toLowerCase(Locale.ROOT);
        String stripped = TAG.matcher(COMMENT.matcher(NOISE.matcher(html).replaceAll(" ")).replaceAll(" ")).replaceAll(" ");
        String text = decode(stripped).replaceAll("\\s+", " ").trim();
        List<Link> links = new ArrayList<>();
        Matcher lm = LINK.matcher(html);
        while (lm.find() && links.size() < 500) {
            try { links.add(new Link(base.resolve(lm.group(1).trim()).toString(), TAG.matcher(lm.group(2)).replaceAll(" ").trim().toLowerCase(Locale.ROOT))); }
            catch (IllegalArgumentException ignored) { /* not a valid URL */ }
        }
        List<String> mailto = new ArrayList<>();
        Matcher mm = MAILTO.matcher(html);
        while (mm.find()) {
            try { mailto.add(URLDecoder.decode(mm.group(1), StandardCharsets.UTF_8)); } catch (IllegalArgumentException e) { mailto.add(mm.group(1)); }
        }
        List<String> tel = new ArrayList<>();
        Matcher tm = TEL.matcher(html);
        while (tm.find()) {
            String n;
            try { n = URLDecoder.decode(tm.group(1).replace("+", "%2B"), StandardCharsets.UTF_8).trim(); }   // keep the + of +49...
            catch (IllegalArgumentException e) { n = tm.group(1).trim(); }
            if (!n.isEmpty()) tel.add(n);
        }
        return new Parsed(title, decode(desc).trim(), lang, text, links, mailto, tel);
    }

    private static final Pattern EMAIL = Pattern.compile("[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern BAD_EMAIL = Pattern.compile("\\.(png|jpe?g|gif|svg|webp|css|js)$|^(no-?reply|donotreply|postmaster|abuse|privacy|gdpr|datenschutz|webmaster)@|@(sentry|wixpress|example\\.com|domain\\.com|email\\.com)", Pattern.CASE_INSENSITIVE);
    private static final List<String> GOOD = List.of("sales", "info", "contact", "hello", "office", "enquiries", "inquiries", "kontakt", "vertrieb", "verkauf", "business", "partners");

    /** Up to 6 addresses, the company's own sales/info addresses first. */
    public static List<String> extractEmails(List<String> texts, String domain) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String t : texts) { Matcher m = EMAIL.matcher(t); while (m.find()) set.add(m.group().toLowerCase(Locale.ROOT).replaceFirst("\\.$", "")); }
        return set.stream().filter(e -> !BAD_EMAIL.matcher(e).find())
                .sorted(Comparator.comparingDouble(e -> rank(e, domain))).limit(6).toList();
    }

    /** A number written after a label such as "Tel:", "Phone", "Call us", "Telefon", "Tél.", "Teléfono". */
    private static final Pattern LABELLED_PHONE = Pattern.compile(
            "(?:tel(?:ephone|efon|éfono|efono|\\.)?|t[ée]l(?:[ée]phone)?|phone|call(?: us)?|fon|mobile|mobil|whatsapp|llamar|telefoon)\\s*[:.]?\\s*(\\+?\\(?\\d[\\d\\s().\\-/]{6,18}\\d)",
            Pattern.CASE_INSENSITIVE);

    /** Up to 3 phone numbers: tel: links first (they are meant for people to call), then numbers written after a "Phone" style label. */
    public static List<String> extractPhones(List<String> telLinks, List<String> texts) {
        LinkedHashMap<String, String> byDigits = new LinkedHashMap<>();   // keyed by digits so the same number is listed once
        for (String raw : telLinks) addPhone(byDigits, raw);
        for (String t : texts) { Matcher m = LABELLED_PHONE.matcher(t); while (m.find()) addPhone(byDigits, m.group(1)); }
        return byDigits.values().stream().limit(3).toList();
    }

    private static void addPhone(Map<String, String> out, String raw) {
        String shown = raw.replaceAll("\\s+", " ").trim();
        String digits = shown.replaceAll("\\D", "");
        if (digits.length() < 7 || digits.length() > 15) return;            // too short or too long to be a phone number
        if (digits.chars().distinct().count() < 3) return;                  // 0000000, 1111111...
        out.putIfAbsent(digits, shown);
    }

    private static double rank(String e, String domain) {
        String[] parts = e.split("@", 2);
        String local = parts[0], host = parts.length > 1 ? parts[1] : "";
        int i = GOOD.indexOf(local);
        return (host.equals(domain) || host.endsWith("." + domain) ? 0 : 10) + (i >= 0 ? i / 10.0 : 1);
    }
}
