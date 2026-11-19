package com.kitepost.service.mail;

import com.kitepost.entity.CampaignEntity;
import com.kitepost.entity.Ids;
import com.kitepost.entity.LeadEntity;
import com.kitepost.entity.ReplyEntity;
import com.kitepost.service.pipeline.Text;
import com.kitepost.repository.CampaignRepository;
import com.kitepost.repository.LeadRepository;
import com.kitepost.repository.ReplyRepository;
import com.kitepost.service.ActivityService;
import com.kitepost.service.Entities;
import com.kitepost.service.SuppressionService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

/** Saves a reply against the lead it answers and applies its consequences. */
@Service
public class ReplyRecorder {
    private final ReplyRepository replies;
    private final LeadRepository leads;
    private final CampaignRepository campaigns;
    private final Entities entities;
    private final ReplyAnalyzer analyzer;
    private final SuppressionService suppression;
    private final ActivityService activity;

    public ReplyRecorder(ReplyRepository replies, LeadRepository leads, CampaignRepository campaigns, Entities entities,
                         ReplyAnalyzer analyzer, SuppressionService suppression, ActivityService activity) {
        this.replies = replies;
        this.leads = leads;
        this.campaigns = campaigns;
        this.entities = entities;
        this.analyzer = analyzer;
        this.suppression = suppression;
        this.activity = activity;
    }

    /** Finds the lead a message answers: by Message-ID, then exact address, then domain. */
    public Optional<LeadEntity> match(List<String> messageIds, String fromAddress) {
        if (!messageIds.isEmpty()) {
            Optional<LeadEntity> l = leads.findFirstBySentMessageIdIn(messageIds);
            if (l.isPresent()) return l;
        }
        if (Text.blank(fromAddress)) return Optional.empty();
        String addr = fromAddress.toLowerCase(Locale.ROOT);
        Optional<LeadEntity> l = leads.findFirstByEmailIgnoreCaseAndSentMessageIdIsNotNull(addr);
        if (l.isPresent() || !addr.contains("@")) return l;
        String[] parts = addr.substring(addr.indexOf('@') + 1).split("\\.");
        List<String> candidates = new ArrayList<>();
        for (int i = 0; i < parts.length - 1; i++) candidates.add(String.join(".", Arrays.copyOfRange(parts, i, parts.length)));
        return candidates.isEmpty() ? Optional.empty() : leads.findFirstByDomainInAndSentMessageIdIsNotNullOrderByApprovedAtDesc(candidates);
    }

    public Optional<ReplyEntity> record(LeadEntity leadIn, ReplyAnalyzer.Incoming m) {
        if (!Text.blank(m.messageId()) && replies.existsByMessageId(m.messageId())) return Optional.empty();
        CampaignEntity c = campaigns.findById(leadIn.campaignId).orElse(null);
        if (c == null) return Optional.empty();
        LeadEntity lead = leads.findById(leadIn.id).orElse(leadIn);
        if (Text.blank(lead.contactFirst) && !Text.blank(m.fromName())) {
            String first = m.fromName().split("[\\s(<]")[0];
            lead = entities.patchLead(lead.id, l -> l.contactFirst = Text.cut(first, 100)).orElse(lead);
        }
        ReplyAnalyzer.Analysis a = analyzer.analyse(m, lead, c);
        ReplyEntity r = new ReplyEntity();
        r.id = Ids.next("r");
        r.leadId = lead.id;
        r.campaignId = c.id;
        r.company = lead.company;
        r.domain = lead.domain;
        r.lang = lead.draft != null ? lead.draft.language() : Objects.toString(lead.lang, "en");
        r.fromAddress = Text.cut(Objects.toString(m.from(), ""), 320);
        r.fromName = Text.cut(Text.blank(m.fromName()) ? r.fromAddress : m.fromName(), 320);
        r.subject = Text.cut(m.subject(), 1000);
        r.body = Text.cut(Objects.toString(m.text(), ""), 50_000);
        r.messageId = Text.cut(m.messageId(), 255);
        r.receivedAt = m.date() == null ? Ids.now() : Instant.parse(m.date());
        r.intent = a.intent();
        r.summary = a.summary();
        r.translation = m.translation() != null ? m.translation() : a.translation();
        r.draft = a.draft();
        r.analysisSource = a.source();
        r.unread = true;
        r.simulated = m.simulated();
        replies.save(r);

        String intent = a.intent();
        entities.patchLead(lead.id, l -> {
            if ("bounce".equals(intent)) { l.status = "bounced"; l.error = "The email bounced."; }
            else if (!"out_of_office".equals(intent)) l.status = "replied";
            l.updatedAt = Ids.now();
        });
        if (("unsubscribe".equals(intent) || "not_interested".equals(intent)) && !Text.blank(lead.email)) suppression.suppress(lead.email, intent);
        String who = r.fromName.contains(lead.company) ? r.fromName : r.fromName + " (" + lead.company + ")";
        activity.add(c.id, "bounce".equals(intent) ? "error" : "reply", ReplyAnalyzer.INTENTS.get(intent) + ": reply from " + who, lead.id);
        return Optional.of(r);
    }
}
