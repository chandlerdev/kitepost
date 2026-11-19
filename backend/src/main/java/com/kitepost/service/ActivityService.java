package com.kitepost.service;

import com.kitepost.entity.ActivityEntity;
import com.kitepost.entity.Ids;
import com.kitepost.repository.ActivityRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ActivityService {
    private final ActivityRepository repo;

    public ActivityService(ActivityRepository repo) { this.repo = repo; }

    public void add(String campaignId, String kind, String text, String leadId) {
        ActivityEntity a = new ActivityEntity();
        a.id = Ids.next("a");
        a.campaignId = campaignId;
        a.kind = kind == null ? "info" : kind.length() > 30 ? kind.substring(0, 30) : kind;
        a.text = text.length() > 1000 ? text.substring(0, 1000) : text;
        a.leadId = leadId;
        a.at = Ids.now();
        repo.save(a);
    }

    public void add(String campaignId, String kind, String text) { add(campaignId, kind, text, null); }

    public List<ActivityEntity> recent(String campaignId, int limit) {
        return repo.findByCampaignIdOrderByAtDescIdDesc(campaignId, PageRequest.of(0, limit));
    }
}
