package com.kitepost.service.pipeline;

import com.kitepost.config.AppProperties;
import com.kitepost.config.Json;
import com.kitepost.entity.CampaignEntity;
import com.kitepost.entity.Models.Query;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** Web search: Brave, SerpAPI (Google results), Google Programmable Search, or demo companies. */
@Component
public class SearchService {
    public record Result(String url, String title, String snippet) {}

    private final AppProperties.Search cfg;
    private final MockCompanies mock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public SearchService(AppProperties props, MockCompanies mock) {
        this.cfg = props.search();
        this.mock = mock;
    }

    public String provider() { return cfg.effectiveProvider(); }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    private Map<String, Object> getJson(String url, Map<String, String> headers) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("Accept", "application/json").GET();
        headers.forEach(b::header);
        HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() / 100 != 2) throw new IOException("search HTTP " + res.statusCode() + ": " + Text.cut(res.body(), 200));
        return Json.read(res.body(), Json.MAP);
    }

    @SuppressWarnings("unchecked")
    private static List<Result> results(Object list, String urlKey, String titleKey, String snippetKey) {
        List<Result> out = new ArrayList<>();
        if (list instanceof List<?> items) for (Object o : items) {
            if (o instanceof Map<?, ?> m) {
                Map<String, Object> r = (Map<String, Object>) m;
                String u = Json.str(r, urlKey);
                if (u != null) out.add(new Result(u, Objects.toString(Json.str(r, titleKey), ""), Objects.toString(Json.str(r, snippetKey), "")));
            }
        }
        return out;
    }

    /** Google Maps listings through SerpAPI: real local businesses with a website, phone and address. */
    private List<Result> mapsSearch(Query q) throws IOException, InterruptedException {
        String text = q.location() != null && !q.location().isBlank() && !q.q().toLowerCase().contains(q.location().toLowerCase())
                ? q.q() + " in " + q.location() : q.q();
        Map<String, Object> j = getJson("https://serpapi.com/search.json?engine=google_maps&type=search&q=" + enc(text)
                + "&hl=en&api_key=" + enc(cfg.serpapiKey()), Map.of());
        List<Result> out = new ArrayList<>();
        if (j.get("local_results") instanceof List<?> items) for (Object o : items) {
            if (!(o instanceof Map<?, ?> m)) continue;
            @SuppressWarnings("unchecked") Map<String, Object> r = (Map<String, Object>) m;
            String site = Json.str(r, "website");
            if (site == null || site.isBlank() || site.contains("wa.me/") || site.contains("facebook.com/") || site.contains("instagram.com/")) continue;   // no company site to read or email
            List<String> bits = new ArrayList<>();
            for (String k : List.of("type", "address", "phone")) { String v = Json.str(r, k); if (v != null && !v.isBlank()) bits.add(v); }
            Object rating = r.get("rating");
            if (rating != null) bits.add("rating " + rating);
            out.add(new Result(site, Objects.toString(Json.str(r, "title"), ""), "Google Maps: " + String.join(" | ", bits)));
        }
        return out;
    }

    private List<Result> mapsSearchSafe(Query q) throws InterruptedException {
        try { return mapsSearch(q); }
        catch (IOException e) { return List.of(); }   // web results still count if Maps fails
    }

    public List<Result> search(Query q, CampaignEntity c) throws IOException, InterruptedException {
        return search(q, c, provider());
    }

    private List<Result> search(Query q, CampaignEntity c, String provider) throws IOException, InterruptedException {
        int n = Math.max(1, cfg.resultsPerQuery());
        return switch (provider) {
            case "brave" -> results(Json.obj(getJson("https://api.search.brave.com/res/v1/web/search?q=" + enc(q.q()) + "&count=" + Math.min(n, 20) + "&country=" + enc(q.gl()),
                    Map.of("X-Subscription-Token", cfg.braveKey())), "web").get("results"), "url", "title", "description");
            case "serpapi" -> results(getJson("https://serpapi.com/search.json?engine=google&q=" + enc(q.q()) + "&gl=" + enc(q.gl()) + "&num=" + Math.min(n, 20)
                    + "&api_key=" + enc(cfg.serpapiKey()), Map.of()).get("organic_results"), "link", "title", "snippet");
            case "maps" -> mapsSearch(q);
            case "serpapi+maps" -> {
                List<Result> web = search(new Query(q.q(), q.country(), q.gl(), q.location()), c, "serpapi");
                List<Result> out = new ArrayList<>(mapsSearchSafe(q));   // listings first: real local businesses
                Set<String> seen = new HashSet<>();
                out.forEach(r -> seen.add(SiteReader.domainOf(r.url())));
                for (Result r : web) if (seen.add(SiteReader.domainOf(r.url()))) out.add(r);
                yield out;
            }
            case "google" -> results(getJson("https://www.googleapis.com/customsearch/v1?key=" + enc(cfg.googleKey()) + "&cx=" + enc(cfg.googleCx())
                    + "&q=" + enc(q.q()) + "&gl=" + enc(q.gl()) + "&num=" + Math.min(n, 10), Map.of()).get("items"), "link", "title", "snippet");
            case "demo" -> mock.search(q, c);
            default -> throw new IOException("Unknown SEARCH_PROVIDER \"" + provider + "\"");
        };
    }
}
