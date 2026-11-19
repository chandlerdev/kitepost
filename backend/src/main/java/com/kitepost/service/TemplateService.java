package com.kitepost.service;

import com.kitepost.dto.Dtos.TemplateIn;
import com.kitepost.dto.Dtos.TemplateOut;
import com.kitepost.entity.Ids;
import com.kitepost.entity.TemplateEntity;
import com.kitepost.exception.ApiException;
import com.kitepost.repository.CampaignRepository;
import com.kitepost.repository.TemplateRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/** Templates are written by hand, one set per campaign, and chosen for each lead in Review. */
@Service
public class TemplateService {
    public static final Set<String> TYPES = Set.of("friendly", "formal", "request", "follow-up", "other");

    private final TemplateRepository templates;
    private final CampaignRepository campaigns;
    private final AuditService audit;

    public TemplateService(TemplateRepository templates, CampaignRepository campaigns, AuditService audit) {
        this.templates = templates;
        this.campaigns = campaigns;
        this.audit = audit;
    }

    public List<TemplateOut> list(String campaignId) {
        List<TemplateEntity> rows = campaignId == null || campaignId.isBlank() ? templates.findAllByOrderByCreatedAtAsc()
                : templates.findByCampaignIdOrderByCreatedAtAsc(campaignId);
        return rows.stream().map(TemplateOut::of).toList();
    }

    public TemplateEntity mustGet(String id) { return templates.findById(id).orElseThrow(() -> ApiException.notFound("Template not found")); }

    private void apply(TemplateEntity t, TemplateIn b) {
        ApiException.require(campaigns.existsById(b.campaignId()), "That campaign doesn't exist.");
        ApiException.require(TYPES.contains(b.type()), "Unknown email type.");
        t.campaignId = b.campaignId();
        t.name = b.name().trim();
        t.type = b.type();
        t.language = b.language().trim();
        t.greeting = b.greeting().trim();
        t.subject = b.subject().trim();
        t.body = b.body();
        t.updatedAt = Ids.now();
    }

    public TemplateOut create(TemplateIn b) {
        TemplateEntity t = new TemplateEntity();
        t.id = Ids.next("t");
        t.createdAt = Ids.now();
        apply(t, b);
        templates.save(t);
        audit.record("template.create", t.id, t.name);
        return TemplateOut.of(t);
    }

    public TemplateOut update(String id, TemplateIn b) {
        TemplateEntity t = mustGet(id);
        apply(t, b);
        templates.save(t);
        audit.record("template.update", t.id, t.name);
        return TemplateOut.of(t);
    }

    public void delete(String id) {
        TemplateEntity t = mustGet(id);
        templates.delete(t);
        audit.record("template.delete", id, t.name);
    }
}
