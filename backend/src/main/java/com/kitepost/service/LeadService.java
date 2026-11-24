package com.kitepost.service;

import com.kitepost.exception.ApiException;
import com.kitepost.dto.Dtos.*;
import com.kitepost.entity.CampaignEntity;
import com.kitepost.entity.Ids;
import com.kitepost.entity.LeadEntity;
import com.kitepost.entity.Models.Draft;
import com.kitepost.service.pipeline.Drafter;
import com.kitepost.service.pipeline.Text;
import com.kitepost.repository.CampaignRepository;
import com.kitepost.repository.LeadRepository;
import com.kitepost.repository.TemplateRepository;
import com.kitepost.entity.TemplateEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class LeadService {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Set<String> CONTACTED = Set.of("sending", "sent", "replied", "bounced");

    private final LeadRepository leads;
    private final CampaignRepository campaigns;
    private final Entities entities;
    private final CampaignRunner runner;
    private final Drafter drafter;
    private final SuppressionService suppression;
    private final ActivityService activity;
    private final ReferenceData ref;
    private final AuditService audit;
    private final TemplateRepository templates;

    public LeadService(LeadRepository leads, CampaignRepository campaigns, Entities entities, CampaignRunner runner, Drafter drafter,
                       SuppressionService suppression, ActivityService activity, ReferenceData ref, AuditService audit,
                       TemplateRepository templates) {
        this.leads = leads;
        this.campaigns = campaigns;
        this.entities = entities;
        this.runner = runner;
        this.drafter = drafter;
        this.suppression = suppression;
        this.activity = activity;
        this.ref = ref;
        this.audit = audit;
        this.templates = templates;
    }

    LeadEntity mustGet(String id) { return leads.findById(id).orElseThrow(() -> ApiException.notFound("Lead not found")); }
    CampaignEntity campaignOf(LeadEntity l) { return campaigns.findById(l.campaignId).orElseThrow(() -> ApiException.notFound("Campaign not found")); }

    public LeadOut out(LeadEntity l, CampaignEntity c) { return LeadOut.of(l, l.draft != null && c != null ? drafter.footer(c, l) : null); }
    public LeadOut out(LeadEntity l) { return out(l, campaigns.findById(l.campaignId).orElse(null)); }

    public LeadPage list(String campaign, String category, String status, String q, Integer limit) {
        Specification<LeadEntity> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (!Text.blank(campaign)) p.add(cb.equal(root.get("campaignId"), campaign));
            if (!Text.blank(category) && !"all".equals(category)) p.add(cb.equal(root.get("category"), category));
            if (!Text.blank(status) && !"all".equals(status)) p.add(root.get("status").in(Arrays.asList(status.split(","))));
            if (!Text.blank(q)) {
                String like = "%" + q.toLowerCase(Locale.ROOT).replaceAll("[%_\\\\]", "") + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("company")), like), cb.like(cb.lower(root.get("domain")), like), cb.like(cb.lower(root.get("email")), like)));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
        int size = Math.min(500, limit == null || limit < 1 ? 200 : limit);
        Page<LeadEntity> page = leads.findAll(spec, PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "updatedAt")));
        Map<String, CampaignEntity> cache = new HashMap<>();
        List<LeadOut> out = page.getContent().stream()
                .map(l -> out(l, cache.computeIfAbsent(l.campaignId, id -> campaigns.findById(id).orElse(null)))).toList();
        return new LeadPage(page.getTotalElements(), out);
    }


    public record Export(String filename, byte[] bytes) {}

    private static final Map<String, String> STATUS_LABEL = Map.ofEntries(
            Map.entry("review", "To review"), Map.entry("approved", "Queued"), Map.entry("sending", "Queued"), Map.entry("sent", "Sent"),
            Map.entry("replied", "Replied"), Map.entry("bounced", "Bounced"), Map.entry("no_email", "No email"), Map.entry("rejected", "Rejected"),
            Map.entry("failed", "Rejected"), Map.entry("suppressed", "Do not contact"));

    /** An Excel sheet of the customer leads (company, email, contact number) for one campaign, or for all campaigns. */
    public Export exportLeads(String campaign) {
        boolean all = Text.blank(campaign) || "all".equals(campaign);
        String scope = "all-campaigns";
        Map<String, String> names = new HashMap<>();
        campaigns.findAll().forEach(c -> names.put(c.id, c.name));
        if (!all) {
            ApiException.require(names.containsKey(campaign), "Campaign not found");
            scope = names.get(campaign).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
            if (scope.length() > 40) scope = scope.substring(0, 40).replaceAll("-+$", "");
            if (scope.isEmpty()) scope = "campaign";
        }
        Specification<LeadEntity> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("category"), "customer"));
            if (!all) p.add(cb.equal(root.get("campaignId"), campaign));
            return cb.and(p.toArray(new Predicate[0]));
        };
        List<LeadEntity> found = leads.findAll(spec, Sort.by("campaignId", "company"));
        List<List<String>> rows = new ArrayList<>();
        for (LeadEntity l : found) {
            rows.add(Arrays.asList(Objects.toString(l.company, ""), Objects.toString(l.email, ""), Objects.toString(l.phone, ""), Objects.toString(l.url, ""),
                    names.getOrDefault(l.campaignId, ""), STATUS_LABEL.getOrDefault(l.status, Objects.toString(l.status, ""))));
        }
        byte[] bytes = XlsxWriter.write("Leads", List.of("Company", "Email", "Contact number", "Website", "Campaign", "Status"), rows, new int[]{34, 34, 22, 40, 30, 16});
        return new Export("kitepost-leads-" + scope + "-" + java.time.LocalDate.now() + ".xlsx", bytes);
    }

    public LeadOut get(String id) { return out(mustGet(id)); }

    public LeadOut patch(String id, LeadPatch b) {
        LeadEntity current = mustGet(id);
        CampaignEntity c = campaignOf(current);
        if (b.draft() != null || b.templateId() != null) ApiException.require(!CONTACTED.contains(current.status), "This email has already been sent.");
        TemplateEntity chosen = null;
        if (b.templateId() != null && !b.templateId().isBlank()) {
            chosen = templates.findById(b.templateId()).orElseThrow(() -> ApiException.notFound("Template not found"));
            ApiException.require(chosen.campaignId.equals(current.campaignId), "That template belongs to a different campaign.");
        }
        final TemplateEntity template = chosen;
        if (b.email() != null && !b.email().isBlank()) ApiException.require(EMAIL.matcher(b.email().trim()).matches(), "That email address doesn't look right.");
        if (b.category() != null && !b.category().equals(current.category)) {
            ApiException.require(ref.isCategory(b.category()), "Unknown category.");
            ApiException.require(!CONTACTED.contains(current.status), "This company has already been contacted.");
        }
        boolean[] categoryChanged = {false};
        LeadEntity saved = entities.patchLead(id, l -> {
            if (b.email() != null) {
                l.email = b.email().isBlank() ? null : b.email().trim().toLowerCase(Locale.ROOT);
                if ("customer".equals(l.category) && ("no_email".equals(l.status) || "review".equals(l.status))) {
                    Draft keep = "review".equals(l.status) ? l.draft : null;
                    runner.prepareForReview(c, l);
                    if (keep != null && "review".equals(l.status)) l.draft = keep;   // a new address keeps your edited draft
                }
            }
            if (template != null) {
                l.templateId = template.id;
                l.draft = drafter.fromTemplate(template, c, l);
            }
            if (b.draft() != null) {
                String lang = l.draft == null ? Drafter.language(c, l) : l.draft.language();
                l.draft = new Draft(b.draft().subject().trim(), b.draft().body(), lang, "you");
            }
            if (b.category() != null && !b.category().equals(l.category)) {
                l.category = b.category();
                l.categorySource = "you";
                l.reason = "Marked as " + b.category() + " by you.";
                if ("customer".equals(b.category())) runner.prepareForReview(c, l);
                else l.status = "classified";
                categoryChanged[0] = true;
            }
            l.updatedAt = Ids.now();
        }).orElseThrow(() -> ApiException.notFound("Lead not found"));
        if (categoryChanged[0]) {
            activity.add(c.id, "info", "You marked " + saved.company + " as " + saved.category, saved.id);
            audit.record("lead.category", saved.id, saved.company + " → " + saved.category);
        }
        if (b.draft() != null) audit.record("lead.edit", saved.id, saved.company);
        return out(saved, c);
    }

    private void checkApprovable(LeadEntity l) {
        ApiException.require("customer".equals(l.category), l.company + " is not marked as a customer.");
        ApiException.require(!Text.blank(l.email), l.company + " has no email address.");
        ApiException.require(l.draft != null, "Choose a template for " + l.company + " first.");
        ApiException.require(Set.of("review", "rejected", "failed").contains(l.status), l.company + " can't be approved in its current state (" + l.status + ").");
        ApiException.require(!suppression.isSuppressed(l.email), l.email + " is on your do-not-contact list.");
    }

    private LeadEntity approveOne(String id) {
        checkApprovable(mustGet(id));
        LeadEntity saved = entities.patchLead(id, l -> {
            checkApprovable(l);
            l.status = "approved";
            l.approvedAt = Ids.now();
            l.updatedAt = Ids.now();
            l.error = null;
        }).orElseThrow(() -> ApiException.notFound("Lead not found"));
        activity.add(saved.campaignId, "approved", "Approved: " + saved.company, saved.id);
        audit.record("lead.approve", saved.id, saved.company + " <" + saved.email + ">");
        return saved;
    }

    public ApproveManyOut approveMany(List<String> ids) {
        int ok = 0;
        List<Skipped> skipped = new ArrayList<>();
        for (String id : new LinkedHashSet<>(ids)) {
            try { approveOne(id); ok++; }
            catch (ApiException e) { skipped.add(new Skipped(id, e.getMessage())); }
        }
        return new ApproveManyOut(ok, skipped);
    }

    public LeadOut action(String id, String action) {
        LeadEntity l = mustGet(id);
        switch (action) {
            case "approve" -> { return out(approveOne(id)); }
            case "reject" -> {
                ApiException.require(Set.of("review", "approved", "no_email", "failed").contains(l.status), "This email can't be rejected now.");
                LeadEntity s = entities.patchLead(id, x -> { x.status = "rejected"; x.updatedAt = Ids.now(); }).orElseThrow();
                activity.add(s.campaignId, "info", "Rejected: " + s.company, s.id);
                audit.record("lead.reject", s.id, s.company);
                return out(s);
            }
            case "unapprove" -> {
                ApiException.require("approved".equals(l.status), "Only queued emails can be moved back to review.");
                LeadEntity s = entities.patchLead(id, x -> {
                    ApiException.require("approved".equals(x.status), "This email is already being sent.");
                    x.status = "review"; x.updatedAt = Ids.now();
                }).orElseThrow();
                audit.record("lead.unapprove", s.id, s.company);
                return out(s);
            }
            default -> throw ApiException.notFound("Unknown action \"" + action + "\"");
        }
    }
}
