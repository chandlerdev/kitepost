package com.kitepost.service;

import com.kitepost.entity.Models.Stats;
import com.kitepost.repository.LeadRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dashboard counts from one grouped query. */
@Service
public class StatsService {
    private static final Set<String> SENT = Set.of("sent", "replied", "bounced");
    private final LeadRepository leads;

    public StatsService(LeadRepository leads) { this.leads = leads; }

    private static final class Acc {
        int found, analysed, customer, partner, vendor, competitor, irrelevant, unreadable, review, noEmail, approved, sent, replied, bounced, rejected;
        Stats toStats() { return new Stats(found, analysed, customer, partner, vendor, competitor, irrelevant, unreadable, review, noEmail, approved, sent, replied, bounced, rejected); }
        void add(String status, String category, int n) {
            found += n;
            if (!"analyzing".equals(status)) analysed += n;
            if (category != null) switch (category) {
                case "customer" -> customer += n;
                case "partner" -> partner += n;
                case "vendor" -> vendor += n;
                case "competitor" -> competitor += n;
                case "irrelevant" -> irrelevant += n;
                default -> { }
            }
            switch (status) {
                case "skipped" -> unreadable += n;
                case "review" -> review += n;
                case "no_email" -> noEmail += n;
                case "approved" -> approved += n;
                case "rejected" -> rejected += n;
                default -> { }
            }
            if (SENT.contains(status)) sent += n;
            if ("replied".equals(status)) replied += n;
            if ("bounced".equals(status)) bounced += n;
        }
    }

    public static Stats empty() { return new Acc().toStats(); }

    private static Map<String, Stats> fold(List<Object[]> rows) {
        Map<String, Acc> acc = new HashMap<>();
        for (Object[] r : rows) acc.computeIfAbsent((String) r[0], k -> new Acc()).add((String) r[1], (String) r[2], ((Number) r[3]).intValue());
        Map<String, Stats> out = new HashMap<>();
        acc.forEach((k, v) -> out.put(k, v.toStats()));
        return out;
    }

    public Map<String, Stats> all() { return fold(leads.countAllGrouped()); }

    public Stats of(String campaignId) { return fold(leads.countGrouped(campaignId)).getOrDefault(campaignId, empty()); }
}
