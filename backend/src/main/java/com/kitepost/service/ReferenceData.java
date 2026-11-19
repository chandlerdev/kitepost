package com.kitepost.service;

import com.kitepost.config.Json;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Countries, US states, Indian states/UTs, languages, email templates and demo names (resources/data/*.json). */
@Component
public class ReferenceData {
    public record Country(String code, String name, String lang, String gl, String region) {}
    public record Region(String label, List<String> list) {}
    public record Category(String id, String label, String desc) {}
    public record Tone(String id, String label, String desc) {}
    public record Reference(List<Country> countries, Map<String, Region> regions, Map<String, String> languages,
                            List<Category> categories, List<String> goals, List<Tone> tones) {}
    public record Templates(Map<String, Map<String, Object>> outreach, Map<String, String> footer, Map<String, String> replyAnswer,
                            Map<String, Map<String, String>> simulatedReplies, Map<String, List<String>> simulatedFirstNames) {}
    public record MockNames(Map<String, List<String>> surnames, Map<String, List<String>> suffixes, Map<String, List<String>> cities,
                            List<String> blockedDomains, Map<String, List<String>> surnamesByCountry, Map<String, List<String>> suffixesByCountry) {}

    private final Reference reference = load("data/reference.json", Reference.class);
    private final Templates templates = load("data/templates.json", Templates.class);
    private final MockNames mockNames = load("data/mock-companies.json", MockNames.class);

    public Reference reference() { return reference; }
    public Templates templates() { return templates; }
    public MockNames mockNames() { return mockNames; }

    public Optional<Country> country(String code) {
        return reference.countries().stream().filter(c -> c.code().equals(code)).findFirst();
    }
    public String countryName(String code) { return country(code).map(Country::name).orElse(code); }
    public String languageName(String lang) { return reference.languages().getOrDefault(lang, "English"); }
    public boolean isCategory(String id) { return reference.categories().stream().anyMatch(c -> c.id().equals(id)); }

    static String resource(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing resource " + path, e);
        }
    }

    private static <T> T load(String path, Class<T> type) { return Json.read(resource(path), type); }
}
