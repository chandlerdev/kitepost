package com.kitepost.service.pipeline;

import com.kitepost.config.AppProperties;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads a company website: homepage plus up to two contact/about pages. */
@Component
public class SiteReader {
    public record Site(String url, String domain, String title, String description, String lang, String text, List<String> emails, List<String> pages) {}

    private static final int MAX_BYTES = 1_500_000;
    private static final int MAX_REDIRECTS = 4;
    private static final Pattern CONTACT = Pattern.compile("(contact|kontakt|about|ueber-uns|uber-uns|impressum|imprint|team|company|nous-contacter|contacto|chi-siamo)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHARSET = Pattern.compile("charset=([\\w-]+)", Pattern.CASE_INSENSITIVE);

    private final AppProperties.Fetch cfg;
    private final MockCompanies mock;
    private final HttpClient http;
    private final Map<String, Boolean> robots = new ConcurrentHashMap<>();

    public SiteReader(AppProperties props, MockCompanies mock) {
        this.cfg = props.fetch();
        this.mock = mock;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(cfg.timeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)   // redirects are followed by hand, each one checked
                .build();
    }

    public static String domainOf(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "");
        } catch (IllegalArgumentException e) { return ""; }
    }

    record Page(String html, URI finalUri) {}

    Page get(URI uri) throws IOException, InterruptedException {
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            SsrfGuard.check(uri);
            HttpRequest req = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(cfg.timeoutMs()))
                    .header("User-Agent", cfg.userAgent()).header("Accept", "text/html,application/xhtml+xml").GET().build();
            HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            int code = res.statusCode();
            if (code >= 300 && code < 400) {
                res.body().close();
                String loc = res.headers().firstValue("location").orElseThrow(() -> new IOException("redirect without a location"));
                uri = uri.resolve(loc);
                continue;
            }
            try (InputStream in = res.body()) {
                if (code / 100 != 2) throw new IOException("HTTP " + code);
                String type = res.headers().firstValue("content-type").orElse("");
                if (!type.matches("(?i).*(html|xml|text).*")) throw new IOException("not an HTML page");
                Matcher cm = CHARSET.matcher(type);
                Charset cs = StandardCharsets.UTF_8;
                if (cm.find()) { try { cs = Charset.forName(cm.group(1)); } catch (Exception ignored) { /* keep UTF-8 */ } }
                return new Page(new String(readLimited(in), cs), uri);
            }
        }
        throw new IOException("too many redirects");
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, Math.min(n, MAX_BYTES - out.size()));
            if (out.size() >= MAX_BYTES) break;
        }
        return out.toByteArray();
    }

    boolean allowedByRobots(URI origin) {
        if (!cfg.respectRobots()) return true;
        return robots.computeIfAbsent(origin.toString(), k -> {
            try {
                Page p = get(origin.resolve("/robots.txt"));
                boolean applies = false;
                for (String raw : p.html().split("\\r?\\n")) {
                    String line = raw.split("#", 2)[0].trim();
                    int i = line.indexOf(':');
                    if (i < 0) continue;
                    String key = line.substring(0, i).trim(), val = line.substring(i + 1).trim();
                    if (key.equalsIgnoreCase("user-agent")) applies = val.equals("*") || val.toLowerCase(Locale.ROOT).contains("kitepost");
                    else if (applies && key.equalsIgnoreCase("disallow") && val.equals("/")) return false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) { /* no robots.txt */ }
            return true;
        });
    }

    public Site read(String url) throws IOException, InterruptedException {
        String domain = domainOf(url);
        if (MockCompanies.isMockDomain(domain)) return mock.site(domain);
        URI u = URI.create(url.trim());
        URI origin = URI.create(u.getScheme() + "://" + u.getRawAuthority());
        if (!allowedByRobots(origin)) throw new IOException("robots.txt disallows crawling");
        Page home = get(origin.resolve("/"));
        HtmlParser.Parsed p = HtmlParser.parse(home.html(), home.finalUri());
        List<String> extra = p.links().stream()
                .filter(l -> domainOf(l.href()).equals(domain) && CONTACT.matcher(l.href() + " " + l.label()).find())
                .map(l -> l.href().split("#", 2)[0]).distinct().limit(2).toList();
        List<String> pages = new ArrayList<>(List.of(home.finalUri().toString()));
        List<String> texts = new ArrayList<>(List.of(p.text(), String.join(" ", p.mailto())));
        for (String e : extra) {
            try {
                Page r = get(URI.create(e));
                HtmlParser.Parsed q = HtmlParser.parse(r.html(), r.finalUri());
                texts.add(q.text());
                texts.add(String.join(" ", q.mailto()));
                pages.add(e);
            } catch (IOException | IllegalArgumentException ignored) { /* skip this page */ }
        }
        String all = String.join(" \n ", texts);
        return new Site(home.finalUri().toString(), domain, p.title(), p.description(), p.lang(),
                all.length() > 12000 ? all.substring(0, 12000) : all, HtmlParser.extractEmails(texts, domain), pages);
    }
}
