package com.kitepost.entity;

import java.util.List;
import java.util.Map;

/**
 * Value objects shared by the database (stored as JSON) and the API.
 * Field names match the front end's lib/types.ts.
 */
public final class Models {
    private Models() {}

    public record Profile(String id, String name, String why, List<String> keywords, boolean on) {}

    public record Signals(List<String> competitor, List<String> vendor, List<String> partner) {
        public static Signals empty() { return new Signals(List.of(), List.of(), List.of()); }
    }

    public record Strategy(String offer, String core, String source, List<Profile> profiles,
                           Map<String, List<String>> localKeywords, Signals signals) {}

    public record Sender(String name, String company, String email, String website, String role, String phone, String address) {}

    public record Query(String q, String country, String gl, String location) {}

    public record CurrentSearch(String country, String location, String query) {}

    public record Draft(String subject, String body, String language, String source) {}

    public record SentInfo(String at, String messageId, boolean dryRun, String to, String subject, String text) {}

    public record Answer(String at, String body, String messageId, boolean dryRun) {}

    public record Stats(int found, int analysed, int customer, int partner, int vendor, int competitor, int irrelevant,
                        int unreadable, int review, int noEmail, int approved, int sent, int replied, int bounced, int rejected) {}
}
