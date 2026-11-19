package com.kitepost.service.mail;

import com.kitepost.config.AppProperties;
import com.kitepost.entity.CampaignEntity;
import com.kitepost.entity.Ids;
import com.kitepost.entity.LeadEntity;
import com.kitepost.entity.Models.Answer;
import com.kitepost.entity.Models.SentInfo;
import com.kitepost.entity.ReplyEntity;
import com.kitepost.service.pipeline.Drafter;
import com.kitepost.service.pipeline.Text;
import com.kitepost.repository.CampaignRepository;
import com.kitepost.repository.LeadRepository;
import com.kitepost.repository.ReplyRepository;
import com.kitepost.service.ActivityService;
import com.kitepost.service.AuditService;
import com.kitepost.service.Entities;
import com.kitepost.service.MetaService;
import com.kitepost.service.SuppressionService;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Sends approved emails one at a time, with a daily cap and the do-not-contact list checked
 * right before each send. With DRY_RUN=true (the default) nothing leaves the server.
 */
@Service
public class OutboundMail {
    private static final Logger log = LoggerFactory.getLogger(OutboundMail.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppProperties props;
    private final MailConfigService mail;
    private final LeadRepository leads;
    private final CampaignRepository campaigns;
    private final ReplyRepository replies;
    private final Entities entities;
    private final SuppressionService suppression;
    private final ActivityService activity;
    private final MetaService meta;
    private final Drafter drafter;
    private final DemoReplies demoReplies;
    private final AuditService audit;
    private final ReentrantLock sending = new ReentrantLock();
    private volatile AppProperties.Smtp builtFor;      // the settings the cached sender was built from
    private volatile JavaMailSenderImpl built;

    public OutboundMail(AppProperties props, MailConfigService mail, LeadRepository leads, CampaignRepository campaigns, ReplyRepository replies, Entities entities,
                        SuppressionService suppression, ActivityService activity, MetaService meta, Drafter drafter,
                        DemoReplies demoReplies, AuditService audit) {
        this.props = props;
        this.mail = mail;
        this.leads = leads;
        this.campaigns = campaigns;
        this.replies = replies;
        this.entities = entities;
        this.suppression = suppression;
        this.activity = activity;
        this.meta = meta;
        this.drafter = drafter;
        this.demoReplies = demoReplies;
        this.audit = audit;
    }

    /** The mail sender for the current settings, or null in dry-run mode. Rebuilt when the settings change. */
    private JavaMailSenderImpl sender() {
        MailConfigService.Effective e = mail.current();
        if (!e.smtpEnabled()) return null;
        JavaMailSenderImpl s = built;
        if (s == null || !e.smtp().equals(builtFor)) {
            s = buildSender(e.smtp());
            built = s;
            builtFor = e.smtp();
        }
        return s;
    }

    public static JavaMailSenderImpl buildSender(AppProperties.Smtp cfg) {
        JavaMailSenderImpl s = new JavaMailSenderImpl();
        s.setHost(cfg.host());
        s.setPort(cfg.port());
        if (AppProperties.has(cfg.user())) { s.setUsername(cfg.user()); s.setPassword(cfg.pass()); }
        s.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties p = s.getJavaMailProperties();
        p.put("mail.smtp.auth", String.valueOf(AppProperties.has(cfg.user())));
        if (cfg.ssl()) p.put("mail.smtp.ssl.enable", "true");
        else { p.put("mail.smtp.starttls.enable", "true"); p.put("mail.smtp.starttls.required", "true"); }
        p.put("mail.smtp.ssl.checkserveridentity", "true");
        p.put("mail.smtp.connectiontimeout", "15000");
        p.put("mail.smtp.timeout", "30000");
        p.put("mail.smtp.writetimeout", "30000");
        return s;
    }

    public boolean live() { return sender() != null; }

    private static String escape(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    public record Delivery(String messageId, boolean dryRun) {}

    /** From address: the one set in the server settings (SMTP_FROM / SMTP_USER); campaigns made before that kept their own. */
    private String senderAddress(CampaignEntity c) {
        String set = mail.current().fromAddress();
        if (!Text.blank(set)) return set;
        return c.sender != null && !Text.blank(c.sender.email()) ? c.sender.email() : "noreply@kitepost.local";
    }

    private Delivery deliver(String fromName, String fromAddress, String to, String subject, String text, String inReplyTo, String references, String trackLeadId)
            throws MessagingException, UnsupportedEncodingException {
        String domain = fromAddress.contains("@") ? fromAddress.substring(fromAddress.indexOf('@') + 1) : "kitepost.local";
        byte[] b = new byte[8];
        RANDOM.nextBytes(b);
        String messageId = "<kp." + HexFormat.of().formatHex(b) + "@" + domain + ">";
        JavaMailSenderImpl smtp = sender();
        if (smtp == null) return new Delivery(messageId, true);
        Session session = smtp.getSession();
        MimeMessage msg = new MimeMessage(session) {
            @Override protected void updateMessageID() throws MessagingException { setHeader("Message-ID", messageId); } // keep our ID for reply matching
        };
        msg.setFrom(new InternetAddress(fromAddress, fromName, StandardCharsets.UTF_8.name()));
        String replyTo = Text.blank(mail.current().replyAddress()) ? fromAddress : mail.current().replyAddress();
        msg.setReplyTo(new InternetAddress[]{new InternetAddress(replyTo)});
        msg.setRecipient(MimeMessage.RecipientType.TO, new InternetAddress(to, true));
        msg.setSubject(subject.replaceAll("[\\r\\n]+", " "), StandardCharsets.UTF_8.name());   // no header injection
        String base = props.trackingBase();
        if (trackLeadId != null && base != null) {
            // text + HTML with a 1x1 image: when the image loads, the email was (likely) opened
            String token = messageId.substring(4, messageId.indexOf("@"));
            String html = "<div style=\"font-family:Arial,Helvetica,sans-serif;font-size:14px;line-height:1.5;white-space:pre-wrap\">" + escape(text) + "</div>"
                    + "<img src=\"" + base + "/api/track/open/" + trackLeadId + "/" + token + ".gif\" width=\"1\" height=\"1\" alt=\"\" style=\"display:none\">";
            MimeBodyPart plain = new MimeBodyPart();
            plain.setText(text, StandardCharsets.UTF_8.name());
            MimeBodyPart rich = new MimeBodyPart();
            rich.setContent(html, "text/html; charset=UTF-8");
            MimeMultipart alt = new MimeMultipart("alternative");
            alt.addBodyPart(plain);
            alt.addBodyPart(rich);
            msg.setContent(alt);
        } else {
            msg.setText(text, StandardCharsets.UTF_8.name());
        }
        msg.setHeader("List-Unsubscribe", "<mailto:" + replyTo + "?subject=unsubscribe>");
        if (inReplyTo != null) msg.setHeader("In-Reply-To", inReplyTo);
        if (references != null && !references.isBlank()) msg.setHeader("References", references);
        smtp.send(msg);
        return new Delivery(messageId, false);
    }

    /** Sends one approved lead. Called only after the lead was claimed (status "sending"). */
    void send(LeadEntity lead) {
        CampaignEntity c = campaigns.findById(lead.campaignId).orElse(null);
        if (c == null || lead.draft == null || Text.blank(lead.email)) return;
        if (suppression.isSuppressed(lead.email)) {
            entities.patchLead(lead.id, l -> { l.status = "suppressed"; l.updatedAt = Ids.now(); });
            return;
        }
        String text = lead.draft.body() + drafter.footer(c, lead);
        try {
            Delivery d = deliver(c.sender.name(), senderAddress(c), lead.email, lead.draft.subject(), text, null, null, lead.id);
            SentInfo sent = new SentInfo(Ids.now().toString(), d.messageId(), d.dryRun(), lead.email, lead.draft.subject(), text);
            LeadEntity saved = entities.patchLead(lead.id, l -> {
                l.sent = sent;
                l.sentMessageId = d.messageId();
                l.status = "sent";
                l.updatedAt = Ids.now();
            }).orElseThrow();
            meta.addSentToday();
            if (meta.get("firstSentAt").isEmpty()) meta.set("firstSentAt", sent.at());   // reply tracking starts here
            activity.add(c.id, "sent", (d.dryRun() ? "Dry run: email recorded" : "Email sent") + " to " + lead.company + " <" + lead.email + ">", lead.id);
            if (d.dryRun() && props.simulateReplies()) demoReplies.schedule(saved, c);
        } catch (Exception e) {
            log.warn("Sending to {} failed: {}", lead.email, e.getMessage());
            entities.patchLead(lead.id, l -> { l.status = "failed"; l.error = Text.cut("Sending failed: " + e.getMessage(), 1000); l.updatedAt = Ids.now(); });
            activity.add(c.id, "error", "Could not send to " + lead.company + ": " + Text.cut(e.getMessage(), 300), lead.id);
        }
    }

    /** One email per tick, oldest approval first, skipping campaigns whose sending is paused. */
    public void sendTick() {
        if (!sending.tryLock()) return;
        try {
            if (meta.sentToday() >= props.sending().dailyLimit()) return;
            Map<String, Boolean> paused = new HashMap<>();
            for (LeadEntity l : leads.findByStatusOrderByApprovedAtAsc("approved", PageRequest.of(0, 25))) {
                boolean isPaused = paused.computeIfAbsent(l.campaignId, id -> campaigns.findById(id).map(c -> !c.sending).orElse(true));
                if (isPaused) continue;
                if (leads.claimForSending(l.id) == 1) {        // atomic: safe with several app instances
                    send(leads.findById(l.id).orElseThrow());
                }
                return;
            }
        } finally {
            sending.unlock();
        }
    }

    /** Answers a reply in the same thread. */
    public Delivery answer(ReplyEntity reply, String body) throws MessagingException, UnsupportedEncodingException {
        LeadEntity lead = leads.findById(reply.leadId).orElseThrow();
        CampaignEntity c = campaigns.findById(lead.campaignId).orElseThrow();
        String subject = reply.subject != null && reply.subject.toLowerCase(Locale.ROOT).startsWith("re:") ? reply.subject
                : "Re: " + Objects.toString(reply.subject, lead.draft == null ? "" : lead.draft.subject());
        String refs = String.join(" ", List.of(Objects.toString(lead.sentMessageId, ""), Objects.toString(reply.messageId, ""))).trim();
        Delivery d = deliver(c.sender.name(), senderAddress(c), reply.fromAddress, subject, body, reply.messageId, refs, null);
        reply.answers = new ArrayList<>(reply.answers == null ? List.of() : reply.answers);
        reply.answers.add(new Answer(Ids.now().toString(), body, d.messageId(), d.dryRun()));
        reply.handled = true;
        replies.save(reply);
        activity.add(c.id, "sent", (d.dryRun() ? "Dry run: answer recorded" : "Answer sent") + " to " + reply.fromName, lead.id);
        audit.record("reply.answer", reply.id, reply.fromAddress);
        return d;
    }
}
