package com.kitepost.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kitepost.entity.*;
import com.kitepost.entity.Models.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Request and response shapes. Field names match the front end (frontend/lib/types.ts).
 * Responses are built explicitly from entities, so nothing sensitive is serialized by accident.
 */
public final class Dtos {
    private Dtos() {}

    /* ---------- auth & users ---------- */
    public record LoginRequest(
            @NotBlank(message = "Enter your email address.") @Email(message = "That email address doesn't look right.") @Size(max = 320) String email,
            @NotBlank(message = "Enter your password.") @Size(max = 128) String password) {}

    public record PasswordChangeRequest(@NotBlank(message = "Enter your current password.") @Size(max = 128) String current,
                                        @NotBlank(message = "Enter a new password.") @Size(max = 128) String next) {}

    public record UserCreateRequest(
            @NotBlank(message = "Enter an email address.") @Email(message = "That email address doesn't look right.") @Size(max = 320) String email,
            @NotBlank(message = "Enter a name.") @Size(max = 200) String name,
            @NotNull(message = "Choose a role.") UserAccount.Role role,
            @NotBlank(message = "Set a starting password.") @Size(max = 128) String password) {}

    public record UserPatch(@Size(max = 200) String name, UserAccount.Role role, Boolean enabled) {}

    public record PasswordResetRequest(@NotBlank(message = "Set a new password.") @Size(max = 128) String password) {}

    public record UserOut(String id, String email, String name, String role, boolean enabled, boolean mustChangePassword,
                          Instant lastLoginAt, Instant createdAt, boolean locked) {
        public static UserOut of(UserAccount u) {
            return new UserOut(u.id, u.email, u.name, u.role.name(), u.enabled, u.mustChangePassword, u.lastLoginAt, u.createdAt,
                    u.lockedUntil != null && u.lockedUntil.isAfter(Instant.now()));
        }
    }

    public record AuditOut(String id, Instant at, String userEmail, String action, String target, String detail, String ip) {
        public static AuditOut of(AuditEvent e) { return new AuditOut(e.id, e.at, e.userEmail, e.action, e.target, e.detail, e.ip); }
    }

    /* ---------- status ---------- */
    public record Mode(String database, String search, String ai, String aiModel, String email, String replies,
                       int dailySendLimit, int sendIntervalSeconds, String fromAddress, String replyAddress) {}

    public record Status(Mode mode, long sentToday, long reviewCount, long unreadReplies, long unreadOpens) {}

    /* ---------- mail configuration (Settings) ---------- */
    /** What the form sends. A blank or missing password means "keep the one that is saved". */
    public record MailIn(@Size(max = 255) String smtpHost, @Min(1) @Max(65535) Integer smtpPort, Boolean smtpSsl,
                         @Size(max = 320) String smtpUser, @Size(max = 500) String smtpPass,
                         @Size(max = 320) String fromAddress, Boolean sendReal,
                         @Size(max = 255) String imapHost, @Min(1) @Max(65535) Integer imapPort, Boolean imapSsl,
                         @Size(max = 320) String imapUser, @Size(max = 500) String imapPass,
                         @Size(max = 200) String imapMailbox) {}

    /** What the form shows. Passwords are never included, only whether one is saved. */
    public record MailOut(String source, boolean encryptionReady, boolean passwordProblem,
                          String smtpHost, int smtpPort, boolean smtpSsl, String smtpUser, boolean smtpPassSet,
                          String fromAddress, boolean sendReal,
                          String imapHost, int imapPort, boolean imapSsl, String imapUser, boolean imapPassSet, String imapMailbox,
                          String effectiveFrom, String effectiveReply, Instant updatedAt, String updatedBy) {}

    public record MailTestOut(boolean ok, String message) {}

    /* ---------- campaigns ---------- */
    public record StrategyRequest(@NotBlank(message = "Add a campaign name.") @Size(max = 300) String name,
                                  @NotBlank @Size(min = 30, max = 1500, message = "Describe your offer in 30 to 1,500 characters.") String description,
                                  @Size(max = 40) String goal,
                                  @Size(max = 30) List<@Size(min = 2, max = 2) String> countries) {}

    public record NewCampaignRequest(
            @NotBlank(message = "Add a campaign name.") @Size(max = 300) String name,
            @NotBlank @Size(min = 30, max = 1500, message = "Describe your offer in 30 to 1,500 characters.") String description,
            @Size(max = 40) String goal,
            @NotEmpty(message = "Pick at least one country.") @Size(max = 30) List<@Size(min = 2, max = 2) String> countries,
            Map<String, @Size(max = 60) List<@Size(max = 80) String>> regions,
            @NotNull(message = "Build the customer profiles first.") Strategy strategy,
            @NotNull @Valid SenderIn sender,
            @Size(max = 20) String tone,
            @Size(max = 20) String emailLanguage,
            @Min(1) @Max(1000) Integer targetCustomers,
            @Min(10) @Max(5000) Integer maxSites,
            Boolean start) {}

    public record SenderIn(@NotBlank(message = "Add the sender's name.") @Size(max = 200) String name,
                           @NotBlank(message = "Add your company name.") @Size(max = 200) String company,
                           @Email(message = "Add a valid sender email address.") @Size(max = 320) String email,   // optional: the From / reply addresses come from the server settings
                           @Size(max = 300) String website, @Size(max = 120) String role, @Size(max = 60) String phone,
                           @Size(max = 300) String address) {
        public Sender toSender() {
            return new Sender(name.trim(), company.trim(), email == null ? "" : email.trim(), nz(website), nz(role), nz(phone), nz(address));
        }
    }

    public record CampaignPatch(@Size(max = 300) String name, @Size(max = 20) String tone, Boolean sending,
                                @Min(1) @Max(1000) Integer targetCustomers, @Valid SenderIn sender) {}

    public record MoreRequest(@Min(1) @Max(500) Integer add) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CampaignOut(String id, String name, String description, String goal, List<String> countries,
                              Map<String, List<String>> regions, Strategy strategy, String pitch, Sender sender, String tone,
                              String emailLanguage, int targetCustomers, int maxSites, String status, int queryIndex,
                              CurrentSearch current, String error, Instant createdAt, Instant startedAt, boolean sending,
                              int queryCount, List<String> searched, Stats stats, List<ActivityOut> activity) {
        public static CampaignOut of(CampaignEntity c, Stats stats, List<ActivityOut> activity) {
            List<Query> qs = c.queries == null ? List.of() : c.queries;
            List<String> searched = qs.subList(0, Math.min(c.queryIndex, qs.size())).stream().map(Query::country).distinct().toList();
            return new CampaignOut(c.id, c.name, c.description, c.goal, c.countries, c.regions, c.strategy, c.pitch, c.sender, c.tone,
                    c.emailLanguage, c.targetCustomers, c.maxSites, c.status, c.queryIndex, c.current, c.error, c.createdAt, c.startedAt,
                    c.sending, qs.size(), searched, stats, activity);
        }
    }

    public record ActivityOut(String id, String campaignId, String kind, String text, String leadId, Instant at) {
        public static ActivityOut of(ActivityEntity a) { return new ActivityOut(a.id, a.campaignId, a.kind, a.text, a.leadId, a.at); }
    }

    /* ---------- leads ---------- */
    public record LeadPatch(@Size(max = 320) String email, @Valid DraftIn draft, @Size(max = 20) String category, @Size(max = 40) String templateId) {}

    public record DraftIn(@NotBlank(message = "The email needs a subject.") @Size(max = 200) String subject,
                          @NotBlank(message = "The email needs a body.") @Size(max = 5000) String body) {}

    public record IdsRequest(@NotNull @Size(max = 500) List<@Size(max = 40) String> ids) {}

    public record ApproveManyOut(int approved, List<Skipped> skipped) {}
    public record Skipped(String id, String reason) {}

    public record LeadOut(String id, String campaignId, String domain, String url, String company, String country, String location,
                          String query, String snippet, Instant createdAt, Instant updatedAt, String status, String category,
                          Double confidence, Double score, String reason, String categorySource, String industry, String title,
                          String siteSummary, String lang, List<String> emails, List<String> pages, String email, Draft draft,
                          SentInfo sent, Instant approvedAt, String error, String footer, String templateId, Instant openedAt, int openCount) {
        public static LeadOut of(LeadEntity l, String footer) {
            return new LeadOut(l.id, l.campaignId, l.domain, l.url, l.company, l.country, l.location, l.query, l.snippet, l.createdAt,
                    l.updatedAt, l.status, l.category, l.confidence, l.score, l.reason, l.categorySource, l.industry, l.title,
                    l.siteSummary, l.lang, l.emails, l.pages, l.email, l.draft, l.sent, l.approvedAt, l.error, footer, l.templateId, l.openedAt, l.openCount);
        }
    }

    public record LeadPage(long total, List<LeadOut> leads) {}

    /* ---------- templates ---------- */
    public record TemplateIn(@NotBlank(message = "Choose a campaign.") @Size(max = 40) String campaignId,
                             @NotBlank(message = "Give the template a name.") @Size(max = 200) String name,
                             @NotBlank(message = "Choose an email type.") @Size(max = 30) String type,
                             @NotBlank(message = "Choose a language.") @Size(max = 20) String language,
                             @NotBlank(message = "Add a greeting, for example: Hello {company} team,") @Size(max = 300) String greeting,
                             @NotBlank(message = "The template needs a subject.") @Size(max = 200) String subject,
                             @NotBlank(message = "The template needs a body.") @Size(max = 5000) String body) {}

    public record TemplateOut(String id, String campaignId, String name, String type, String language, String greeting,
                              String subject, String body, Instant createdAt, Instant updatedAt) {
        public static TemplateOut of(TemplateEntity t) {
            return new TemplateOut(t.id, t.campaignId, t.name, t.type, t.language, t.greeting, t.subject, t.body, t.createdAt, t.updatedAt);
        }
    }

    /* ---------- notifications (emails opened) ---------- */
    public record NotificationOut(String leadId, String campaignId, String campaignName, String company, String email,
                                  Instant openedAt, int openCount, boolean seen) {}

    public record NotificationList(long unread, List<NotificationOut> items) {}

    /* ---------- replies ---------- */
    public record ReplyPatch(Boolean handled, Boolean unread, @Size(max = 5000) String draft) {}

    public record AnswerRequest(@NotBlank(message = "Write an answer first.") @Size(max = 5000) String body) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ReplyOut(String id, String leadId, String campaignId, String company, String domain, String lang, String from,
                           String fromName, String subject, String text, String messageId, Instant receivedAt, String intent,
                           String summary, String translation, String draft, String analysisSource, boolean unread, boolean handled,
                           boolean simulated, List<Answer> answers, String campaignName, String leadEmail, SentInfo sentEmail, Boolean dryRun) {
        public static ReplyOut of(ReplyEntity r, String campaignName, LeadEntity lead, Boolean dryRun) {
            return new ReplyOut(r.id, r.leadId, r.campaignId, r.company, r.domain, r.lang, r.fromAddress, r.fromName, r.subject, r.body,
                    r.messageId, r.receivedAt, r.intent, r.summary, r.translation, r.draft, r.analysisSource, r.unread, r.handled,
                    r.simulated, r.answers, campaignName, lead == null ? null : lead.email, lead == null ? null : lead.sent, dryRun);
        }
    }

    /* ---------- do-not-contact ---------- */
    public record SuppressionRequest(@NotBlank(message = "Add an email address or domain.") @Size(max = 320) String value) {}

    public record SuppressionOut(String value, String reason, Instant at) {
        public static SuppressionOut of(SuppressionEntity s) { return new SuppressionOut(s.value, s.reason, s.at); }
    }

    static String nz(String s) { return s == null ? "" : s.trim(); }
}
