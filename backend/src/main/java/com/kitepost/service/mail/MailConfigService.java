package com.kitepost.service.mail;

import com.kitepost.config.AppProperties;
import com.kitepost.dto.Dtos.MailIn;
import com.kitepost.dto.Dtos.MailOut;
import com.kitepost.dto.Dtos.MailTestOut;
import com.kitepost.entity.MailSettingsEntity;
import com.kitepost.exception.ApiException;
import com.kitepost.repository.MailSettingsRepository;
import com.kitepost.security.AppUser;
import com.kitepost.security.CurrentUser;
import com.kitepost.security.SecretBox;
import com.kitepost.service.AuditService;
import jakarta.mail.Folder;
import jakarta.mail.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The mail settings in use: the ones saved in Settings (passwords encrypted in the database) when there
 * are any, otherwise the server settings (.env). Read by the sender, the reply reader and the status page.
 */
@Service
public class MailConfigService {
    private static final Logger log = LoggerFactory.getLogger(MailConfigService.class);
    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9]([A-Za-z0-9.-]{0,253}[A-Za-z0-9])?$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final long TTL_MS = 5_000;       // so a change made on another instance shows up within seconds

    /** The settings the app uses right now. */
    public record Effective(AppProperties.Smtp smtp, AppProperties.Imap imap, boolean dryRun, String source, boolean passwordProblem) {
        public boolean smtpEnabled() { return AppProperties.has(smtp.host()) && !dryRun; }

        /** The address every email is sent from. Null = not set. */
        public String fromAddress() {
            if (AppProperties.has(smtp.from())) return smtp.from().trim();
            return AppProperties.has(smtp.user()) && smtp.user().contains("@") ? smtp.user().trim() : null;
        }

        /** The address replies go to: the mailbox that is read for replies, else the sending address. */
        public String replyAddress() {
            String u = imapUser();
            return AppProperties.has(u) && u.contains("@") && imap.enabled() ? u.trim() : fromAddress();
        }

        /** The reply mailbox's login: its own, or the sending login when none is given. */
        public String imapUser() { return AppProperties.has(imap.user()) ? imap.user() : smtp.user(); }
        public String imapPass() { return AppProperties.has(imap.pass()) ? imap.pass() : smtp.pass(); }
    }

    private final MailSettingsRepository repo;
    private final SecretBox box;
    private final AppProperties props;
    private final AuditService audit;
    private volatile Effective cached;
    private volatile long loadedAt;

    public MailConfigService(MailSettingsRepository repo, SecretBox box, AppProperties props, AuditService audit) {
        this.repo = repo;
        this.box = box;
        this.props = props;
        this.audit = audit;
    }

    public Effective current() {
        Effective e = cached;
        if (e != null && System.currentTimeMillis() - loadedAt < TTL_MS) return e;
        synchronized (this) {
            e = load();
            cached = e;
            loadedAt = System.currentTimeMillis();
            return e;
        }
    }

    private void invalidate() { cached = null; }

    private Effective env() {
        return new Effective(props.smtp(), props.imap(), props.sending().dryRun(), "server", false);
    }

    private Effective load() {
        MailSettingsEntity s;
        try {
            s = repo.findById(MailSettingsEntity.ID).orElse(null);
        } catch (Exception ex) {
            log.warn("Could not read the mail settings, using the server settings: {}", ex.getMessage());
            return env();
        }
        if (s == null) return env();
        String sp = box.decrypt(s.smtpPassEnc);
        String ip = box.decrypt(s.imapPassEnc);
        boolean problem = sp == null || ip == null;
        if (problem) log.warn("A saved mailbox password could not be decrypted: MAIL_ENCRYPTION_KEY is missing or was changed. Enter the passwords again in Settings.");
        AppProperties.Imap base = props.imap();
        return new Effective(
                new AppProperties.Smtp(s.smtpHost, s.smtpPort, s.smtpSsl, s.smtpUser, sp == null ? "" : sp, s.fromAddress),
                new AppProperties.Imap(s.imapHost, s.imapPort, s.imapSsl, s.imapUser, ip == null ? "" : ip, s.imapMailbox, base.pollSeconds()),
                !s.sendReal, "settings", problem);
    }

    /* ---------- the settings page ---------- */

    public MailOut view() {
        Effective e = current();
        MailSettingsEntity s = repo.findById(MailSettingsEntity.ID).orElse(null);
        AppProperties.Smtp sm = e.smtp();
        AppProperties.Imap im = e.imap();
        return new MailOut(e.source(), box.available(), e.passwordProblem(),
                nz(sm.host()), sm.port(), sm.ssl(), nz(sm.user()), AppProperties.has(sm.pass()) || (s != null && !s.smtpPassEnc.isEmpty()),
                nz(sm.from()), !e.dryRun(),
                nz(im.host()), im.port(), im.ssl(), nz(im.user()), AppProperties.has(im.pass()) || (s != null && !s.imapPassEnc.isEmpty()), nz(im.mailbox()),
                e.fromAddress(), e.replyAddress(), s == null ? null : s.updatedAt, s == null ? null : s.updatedBy);
    }

    public MailOut save(MailIn in) {
        MailSettingsEntity old = repo.findById(MailSettingsEntity.ID).orElse(null);
        MailSettingsEntity s = old != null ? old : new MailSettingsEntity();
        Effective env = env();

        String smtpHost = trim(in.smtpHost()), imapHost = trim(in.imapHost());
        String smtpUser = trim(in.smtpUser()), imapUser = trim(in.imapUser());
        String from = trim(in.fromAddress());
        boolean real = Boolean.TRUE.equals(in.sendReal());
        if (!smtpHost.isEmpty()) ApiException.require(HOST.matcher(smtpHost).matches(), "The sending server name doesn't look right (for example smtp.example.com).");
        if (!imapHost.isEmpty()) ApiException.require(HOST.matcher(imapHost).matches(), "The reply server name doesn't look right (for example imap.example.com).");
        if (!from.isEmpty()) ApiException.require(EMAIL.matcher(from).matches(), "The From address doesn't look right.");
        if (!smtpHost.isEmpty())
            ApiException.require(!from.isEmpty() || smtpUser.contains("@"), "Add the From address (the mailbox the emails are sent from).");
        if (real) ApiException.require(!smtpHost.isEmpty(), "Add the sending server before turning on real sending.");

        // A blank password keeps the saved one. The first time, it can also carry over the server's (.env) password for the same login.
        String smtpPass = pick(in.smtpPass(), old == null ? (same(smtpUser, env.smtp().user()) ? env.smtp().pass() : "") : box.decrypt(s.smtpPassEnc));
        String imapPass = pick(in.imapPass(), old == null ? (same(imapUser, env.imap().user()) ? env.imap().pass() : "") : box.decrypt(s.imapPassEnc));
        if (!smtpPass.isEmpty() || !imapPass.isEmpty())
            ApiException.require(box.available(), "Passwords can only be saved after MAIL_ENCRYPTION_KEY is set in the server settings (kitepost_backend/.env). Ask whoever runs the server to add it, then restart the backend.");

        s.smtpHost = smtpHost;
        s.smtpPort = in.smtpPort() == null ? 587 : in.smtpPort();
        s.smtpSsl = Boolean.TRUE.equals(in.smtpSsl());
        s.smtpUser = smtpUser;
        s.smtpPassEnc = box.encrypt(smtpPass);
        s.fromAddress = from;
        s.sendReal = real;
        s.imapHost = imapHost;
        s.imapPort = in.imapPort() == null ? 993 : in.imapPort();
        s.imapSsl = in.imapSsl() == null || in.imapSsl();
        s.imapUser = imapUser;
        s.imapPassEnc = box.encrypt(imapPass);
        s.imapMailbox = trim(in.imapMailbox()).isEmpty() ? "INBOX" : trim(in.imapMailbox());
        s.updatedAt = Instant.now();
        AppUser u = CurrentUser.get().orElse(null);
        s.updatedBy = u == null ? null : u.email();
        repo.save(s);
        invalidate();
        audit.record("mail.settings.save", "mail", "sending " + (smtpHost.isEmpty() ? "none" : smtpHost) + ", replies " + (imapHost.isEmpty() ? "none" : imapHost)
                + ", real sending " + (real ? "on" : "off"));
        return view();
    }

    /** Goes back to the server settings (.env) and forgets the saved ones. */
    public MailOut reset() {
        repo.deleteById(MailSettingsEntity.ID);
        invalidate();
        audit.record("mail.settings.reset", "mail", "back to the server settings");
        return view();
    }

    /* ---------- tests ---------- */

    public MailTestOut testSending() {
        Effective e = current();
        AppProperties.Smtp c = e.smtp();
        if (!AppProperties.has(c.host())) return new MailTestOut(false, "No sending server is set.");
        try {
            OutboundMail.buildSender(c).testConnection();
            return new MailTestOut(true, "Connected to " + c.host() + ":" + c.port() + (AppProperties.has(c.user()) ? " and signed in as " + c.user() : "") + ".");
        } catch (Exception ex) {
            return new MailTestOut(false, "Could not connect: " + reason(ex));
        }
    }

    public MailTestOut testReplies() {
        Effective e = current();
        AppProperties.Imap c = e.imap();
        if (!c.enabled()) return new MailTestOut(false, "No reply server is set.");
        try {
            Store store = ImapPoller.open(e);
            try {
                Folder f = store.getFolder(c.mailbox());
                if (!f.exists()) return new MailTestOut(false, "Signed in, but the folder \"" + c.mailbox() + "\" doesn't exist.");
                return new MailTestOut(true, "Connected to " + c.host() + ":" + c.port() + " and found the folder \"" + c.mailbox() + "\".");
            } finally {
                store.close();
            }
        } catch (Exception ex) {
            return new MailTestOut(false, "Could not connect: " + reason(ex));
        }
    }

    /* ---------- helpers ---------- */

    private static String reason(Exception ex) {
        String m = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage().replaceAll("\\s+", " ").trim();
        return m.length() > 300 ? m.substring(0, 300) : m;
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }
    private static String nz(String s) { return s == null ? "" : s; }
    private static boolean same(String a, String b) { return !a.isEmpty() && b != null && a.toLowerCase(Locale.ROOT).equals(b.trim().toLowerCase(Locale.ROOT)); }

    /** The new password when one was typed, otherwise what was there. */
    private static String pick(String typed, String existing) {
        if (typed != null && !typed.isBlank()) return typed;
        return existing == null ? "" : existing;
    }
}
