package com.kitepost.service;

import com.kitepost.config.AppProperties;
import com.kitepost.config.Json;
import com.kitepost.entity.*;
import com.kitepost.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * SEED_SAMPLE_DATA=true loads the US + India sample campaign (the same one as the demo)
 * into an empty database, with its timestamps moved to "just now".
 */
@Component
@Order(2)
public class SampleDataLoader implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SampleDataLoader.class);
    private static final Pattern ISO = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z$");

    public record SeedFile(List<CampaignEntity> campaigns, List<LeadEntity> leads, List<ReplyEntity> replies,
                           List<ActivityEntity> activity, List<SuppressionEntity> suppression) {}

    private final AppProperties props;
    private final CampaignRepository campaigns;
    private final LeadRepository leads;
    private final ReplyRepository replies;
    private final ActivityRepository activity;
    private final SuppressionRepository suppression;
    private final MetaService meta;

    public SampleDataLoader(AppProperties props, CampaignRepository campaigns, LeadRepository leads, ReplyRepository replies,
                            ActivityRepository activity, SuppressionRepository suppression, MetaService meta) {
        this.props = props;
        this.campaigns = campaigns;
        this.leads = leads;
        this.replies = replies;
        this.activity = activity;
        this.suppression = suppression;
        this.meta = meta;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.seedSample() || campaigns.count() > 0) return;
        Map<String, Object> raw = Json.read(ReferenceData.resource("data/sample-data.json"), Json.MAP);
        long[] latest = {0};
        walk(raw, s -> { latest[0] = Math.max(latest[0], Instant.parse(s).toEpochMilli()); return s; });
        long delta = System.currentTimeMillis() - Duration.ofMinutes(3).toMillis() - latest[0];
        Object shifted = walk(raw, s -> Instant.ofEpochMilli(Instant.parse(s).toEpochMilli() + delta).toString());
        SeedFile seed = Json.MAPPER.convertValue(shifted, SeedFile.class);
        for (CampaignEntity c : seed.campaigns()) { if ("running".equals(c.status)) c.status = "paused"; c.version = null; campaigns.save(c); }
        for (LeadEntity l : seed.leads()) { l.version = null; if (l.sent != null) l.sentMessageId = l.sent.messageId(); leads.save(l); }
        for (ReplyEntity r : seed.replies()) { r.version = null; replies.save(r); }
        activity.saveAll(seed.activity());
        suppression.saveAll(seed.suppression());
        seed.leads().stream().filter(l -> l.sent != null).map(l -> l.sent.at()).sorted().findFirst().ifPresent(t -> meta.set("firstSentAt", t));
        log.info("Sample data loaded: {} campaign(s), {} leads, {} replies", seed.campaigns().size(), seed.leads().size(), seed.replies().size());
    }

    @SuppressWarnings("unchecked")
    private static Object walk(Object o, java.util.function.UnaryOperator<String> f) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            ((Map<String, Object>) m).forEach((k, v) -> out.put(k, walk(v, f)));
            return out;
        }
        if (o instanceof List<?> l) return l.stream().map(x -> walk(x, f)).toList();
        if (o instanceof String s && ISO.matcher(s).matches()) return f.apply(s);
        return o;
    }
}
