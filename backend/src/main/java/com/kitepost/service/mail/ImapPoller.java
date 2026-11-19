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
        Optional<LeadEntity> lead = recorder.match(ids, address);
        if (lead.isEmpty()) return;
        String[] mid = m.getHeader("Message-ID");
        Date received = m.getReceivedDate() != null ? m.getReceivedDate() : m.getSentDate();
        recorder.record(lead.get(), new ReplyAnalyzer.Incoming(address, name, m.getSubject(), textOf(m),
                mid != null && mid.length > 0 ? mid[0] : null, received == null ? null : received.toInstant().toString(), null, false));
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
