package com.kitepost.service.mail;

import com.kitepost.config.AppProperties;
import com.kitepost.entity.LeadEntity;
import com.kitepost.service.MetaService;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/** Reads new messages from the inbox (read-only) and records the ones that answer our emails. */
@Component
public class ImapPoller {
    private static final Logger log = LoggerFactory.getLogger(ImapPoller.class);
    private final MailConfigService mail;
    private final MetaService meta;
    private final ReplyRecorder recorder;
    private final ReentrantLock lock = new ReentrantLock();

    public ImapPoller(MailConfigService mail, MetaService meta, ReplyRecorder recorder) {
        this.mail = mail;
        this.meta = meta;
        this.recorder = recorder;
    }

    public boolean enabled() { return mail.current().imap().enabled(); }

    /** Opens a connection to the reply mailbox with the given settings. The caller closes it. */
    static Store open(MailConfigService.Effective e) throws MessagingException {
        AppProperties.Imap cfg = e.imap();
        Properties p = new Properties();
        String protocol = cfg.ssl() ? "imaps" : "imap";
        p.put("mail.store.protocol", protocol);
        p.put("mail." + protocol + ".ssl.checkserveridentity", "true");
        if (!cfg.ssl()) p.put("mail.imap.starttls.required", "true");
        p.put("mail." + protocol + ".connectiontimeout", "15000");
        p.put("mail." + protocol + ".timeout", "30000");
        Store store = Session.getInstance(p).getStore(protocol);
        store.connect(cfg.host(), cfg.port(), e.imapUser(), e.imapPass());
        return store;
    }

    public void poll() {
        if (!enabled() || !lock.tryLock()) return;
        try {
            Optional<String> firstSent = meta.get("firstSentAt");
            if (firstSent.isEmpty()) return;
            MailConfigService.Effective e = mail.current();
            AppProperties.Imap cfg = e.imap();
            Store store = open(e);
            try {
                Folder folder = store.getFolder(cfg.mailbox());
                folder.open(Folder.READ_ONLY);
                try {
                    UIDFolder uf = (UIDFolder) folder;
                    long last = meta.get("imapLastUid").map(Long::parseLong).orElse(0L);
                    Message[] msgs = last > 0 ? uf.getMessagesByUID(last + 1, UIDFolder.LASTUID)
                            : folder.search(new ReceivedDateTerm(ComparisonTerm.GE, Date.from(Instant.parse(firstSent.get()))));
                    long max = last;
                    for (Message m : msgs) {
                        long uid = uf.getUID(m);
                        if (uid <= last) continue;
                        max = Math.max(max, uid);
                        handle(m);
                    }
                    if (max > last) meta.set("imapLastUid", String.valueOf(max));
                } finally {
                    folder.close(false);
                }
            } finally {
                store.close();
            }
        } catch (Exception e) {
            log.warn("IMAP check failed: {}", e.getMessage());
        } finally {
            lock.unlock();
        }
    }

    private void handle(Message m) throws MessagingException, IOException {
        List<String> ids = new ArrayList<>();
        String[] irt = m.getHeader("In-Reply-To");
        if (irt != null) for (String v : irt) ids.addAll(Arrays.asList(v.trim().split("\\s+")));
        String[] refs = m.getHeader("References");
        if (refs != null) for (String v : refs) ids.addAll(Arrays.asList(v.trim().split("\\s+")));
        ids.removeIf(String::isBlank);
        Address[] from = m.getFrom();
        String address = null, name = null;
        if (from != null && from.length > 0 && from[0] instanceof InternetAddress ia) { address = ia.getAddress(); name = ia.getPersonal(); }
        String kind = kindOf(m);
        boolean fromSystem = address != null && address.toLowerCase(Locale.ROOT).matches("^(mailer-daemon|postmaster)@.*");
        // A delivery report comes from the mail system, not from the company: find which email it is about by our own Message-ID inside it.
        if ("bounce".equals(kind) || fromSystem) ourIds(m, ids, 0);
        Optional<LeadEntity> lead = recorder.match(ids, address);
        if (lead.isEmpty()) return;
        String[] mid = m.getHeader("Message-ID");
        Date received = m.getReceivedDate() != null ? m.getReceivedDate() : m.getSentDate();
        recorder.record(lead.get(), new ReplyAnalyzer.Incoming(address, name, m.getSubject(), textOf(m),
                mid != null && mid.length > 0 ? mid[0] : null, received == null ? null : received.toInstant().toString(), null, false,
                fromSystem && kind == null ? "bounce" : kind));
    }

    private static final Pattern OUR_ID = Pattern.compile("<kp\\.[0-9a-f]{16}@[^>\\s]+>");

    /** What the headers say about the message: "bounce" (delivery report), "auto" (automatic reply), or null (a person wrote it). */
    static String kindOf(Message m) throws MessagingException {
        String ct = m.getContentType() == null ? "" : m.getContentType().toLowerCase(Locale.ROOT);
        if (ct.contains("multipart/report") || ct.contains("delivery-status")) return "bounce";
        String[] auto = m.getHeader("Auto-Submitted");
        if (auto != null && auto.length > 0 && !auto[0].trim().equalsIgnoreCase("no")) return "auto";
        if (m.getHeader("X-Autoreply") != null || m.getHeader("X-Autorespond") != null) return "auto";
        String[] prec = m.getHeader("Precedence");
        if (prec != null && prec.length > 0 && prec[0].toLowerCase(Locale.ROOT).contains("auto_reply")) return "auto";
        return null;
    }

    /** Collects our Message-IDs from anywhere in a message (a delivery report quotes the original's headers). */
    static void ourIds(Part p, List<String> out, int depth) {
        if (depth > 6) return;
        try {
            if (p.isMimeType("multipart/*") && p.getContent() instanceof Multipart mp) {
                for (int i = 0; i < mp.getCount(); i++) ourIds(mp.getBodyPart(i), out, depth + 1);
            } else if (p.isMimeType("message/rfc822") && p.getContent() instanceof Part q) {
                ourIds(q, out, depth + 1);
            } else if (p.isMimeType("text/*")) {
                var mm = OUR_ID.matcher(String.valueOf(p.getContent()));
                while (mm.find()) out.add(mm.group());
            }
        } catch (Exception e) { /* a part that cannot be read is skipped */ }
    }

    /** Plain-text body: prefers text/plain, falls back to HTML with tags removed. */
    static String textOf(Part p) throws MessagingException, IOException {
        if (p.isMimeType("text/plain")) return String.valueOf(p.getContent());
        if (p.isMimeType("text/html")) return String.valueOf(p.getContent()).replaceAll("(?is)<(script|style)\\b.*?</\\1>", " ").replaceAll("<[^>]+>", " ").replaceAll("[ \\t]+", " ");
        if (p.isMimeType("multipart/*") && p.getContent() instanceof Multipart mp) {
            String html = null;
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                if (bp.isMimeType("text/plain")) return String.valueOf(bp.getContent());
                String t = textOf(bp);
                if (!t.isBlank() && html == null) html = t;
            }
            return html == null ? "" : html;
        }
        return "";
    }
}
