package com.kitepost.service;

import com.kitepost.entity.CampaignEntity;
import com.kitepost.entity.LeadEntity;
import com.kitepost.repository.CampaignRepository;
import com.kitepost.repository.LeadRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Read-change-write with optimistic locking: if someone else saved the same row in between
 * (another request, or the background search), the change is re-applied to the fresh copy.
 */
@Component
public class Entities {
    private final CampaignRepository campaigns;
    private final LeadRepository leads;

    public Entities(CampaignRepository campaigns, LeadRepository leads) {
        this.campaigns = campaigns;
        this.leads = leads;
    }

    public Optional<CampaignEntity> patchCampaign(String id, Consumer<CampaignEntity> change) {
        for (int attempt = 1; ; attempt++) {
            Optional<CampaignEntity> c = campaigns.findById(id);
            if (c.isEmpty()) return c;
            change.accept(c.get());
            try { return Optional.of(campaigns.save(c.get())); }
            catch (OptimisticLockingFailureException e) { if (attempt >= 4) throw e; }
        }
    }

    public Optional<LeadEntity> patchLead(String id, Consumer<LeadEntity> change) {
        for (int attempt = 1; ; attempt++) {
            Optional<LeadEntity> l = leads.findById(id);
            if (l.isEmpty()) return l;
            change.accept(l.get());
            try { return Optional.of(leads.save(l.get())); }
            catch (OptimisticLockingFailureException e) { if (attempt >= 4) throw e; }
        }
    }
}
