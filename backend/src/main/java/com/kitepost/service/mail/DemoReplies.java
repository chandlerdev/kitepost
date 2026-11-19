package com.kitepost.service.mail;

import com.kitepost.entity.CampaignEntity;
import com.kitepost.entity.Ids;
import com.kitepost.entity.LeadEntity;
import com.kitepost.repository.LeadRepository;
import com.kitepost.service.ReferenceData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Dry-run mode: some recorded emails get a fictional reply 12–52 seconds later. */
@Component
public class DemoReplies {
    private static final Logger log = LoggerFactory.getLogger(DemoReplies.class);
    private final TaskScheduler scheduler;
    private final LeadRepository leads;
    private final ReplyRecorder recorder;
    private final ReferenceData ref;

    public DemoReplies(TaskScheduler scheduler, LeadRepository leads, ReplyRecorder recorder, ReferenceData ref) {
        this.scheduler = scheduler;
        this.leads = leads;
        this.recorder = recorder;
        this.ref = ref;
    }

    public void schedule(LeadEntity lead, CampaignEntity c) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double r = rnd.nextDouble();
        if (r > 0.45) return;   // most emails get no reply
        String intent = r < 0.22 ? "interested" : r < 0.32 ? "question" : r < 0.40 ? "not_interested" : "out_of_office";
        String lang = lead.draft == null ? "en" : lead.draft.language();
        List<String> names = ref.templates().simulatedFirstNames().getOrDefault(lang, ref.templates().simulatedFirstNames().get("en"));
        String name = names.get(rnd.nextInt(names.size()));
        Map<String, String> byLang = ref.templates().simulatedReplies().get(intent);
        String local = byLang.get(lang);
        String text = fill(local != null ? local : byLang.get("en"), c, lead, name);
        String translation = local != null && !"en".equals(lang) ? fill(byLang.get("en"), c, lead, name) : null;
        long delayMs = 12_000 + (long) (rnd.nextDouble() * 40_000);
        scheduler.schedule(() -> {
            try {
                LeadEntity fresh = leads.findById(lead.id).orElse(null);
                if (fresh == null || !"sent".equals(fresh.status) || fresh.email == null) return;
                recorder.record(fresh, new ReplyAnalyzer.Incoming(fresh.email, name + " (" + fresh.company + ")",
                        "Re: " + (fresh.draft == null ? "" : fresh.draft.subject()), text, "<sim." + Ids.next("m") + "@" + fresh.domain + ">",
                        Ids.now().toString(), translation, true));
            } catch (Exception e) {
                log.warn("Demo reply failed: {}", e.getMessage());
            }
        }, Instant.now().plusMillis(delayMs));
    }

    private String fill(String t, CampaignEntity c, LeadEntity l, String name) {
        return t.replace("{first}", c.sender.name().split(" ")[0]).replace("{name}", name).replace("{company}", l.company)
                .replace("{country}", ref.countryName(l.country));
    }
}
