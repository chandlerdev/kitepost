package com.kitepost.controller;

import com.kitepost.dto.Dtos.NotificationList;
import com.kitepost.dto.Dtos.NotificationOut;
import com.kitepost.entity.CampaignEntity;
import com.kitepost.repository.CampaignRepository;
import com.kitepost.repository.LeadRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Notifications = emails that were opened (likely). Replies stay on the Replies page. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final LeadRepository leads;
    private final CampaignRepository campaigns;

    public NotificationController(LeadRepository leads, CampaignRepository campaigns) {
        this.leads = leads;
        this.campaigns = campaigns;
    }

    @GetMapping
    public NotificationList list() {
        var rows = leads.findTop200ByOpenedAtIsNotNullOrderByOpenedAtDesc();
        Map<String, String> names = campaigns.findAllById(rows.stream().map(l -> l.campaignId).distinct().toList()).stream()
                .collect(Collectors.toMap(c -> c.id, c -> c.name));
        List<NotificationOut> items = rows.stream().map(l -> new NotificationOut(l.id, l.campaignId, names.getOrDefault(l.campaignId, ""),
                l.company, l.email, l.openedAt, l.openCount, l.openSeen)).toList();
        return new NotificationList(leads.countByOpenSeenFalseAndOpenedAtIsNotNull(), items);
    }

    @PostMapping("/seen")
    public Map<String, Object> seen() { return Map.of("marked", leads.markOpensSeen()); }
}
