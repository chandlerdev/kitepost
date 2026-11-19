package com.kitepost.service;

import com.kitepost.exception.ApiException;
import com.kitepost.dto.Dtos.*;
import com.kitepost.entity.CampaignEntity;
import com.kitepost.entity.LeadEntity;
import com.kitepost.entity.ReplyEntity;
import com.kitepost.service.mail.ImapPoller;
import com.kitepost.service.mail.OutboundMail;
import com.kitepost.repository.CampaignRepository;
import com.kitepost.repository.LeadRepository;
import com.kitepost.repository.ReplyRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ReplyService {
    private final ReplyRepository replies;
    private final LeadRepository leads;
    private final CampaignRepository campaigns;
    private final OutboundMail mail;
    private final ImapPoller imap;

    public ReplyService(ReplyRepository replies, LeadRepository leads, CampaignRepository campaigns, OutboundMail mail, ImapPoller imap) {
        this.replies = replies;
        this.leads = leads;
        this.campaigns = campaigns;
        this.mail = mail;
        this.imap = imap;
    }

    private ReplyOut out(ReplyEntity r, Boolean dryRun) {
        String campaignName = campaigns.findById(r.campaignId).map(c -> c.name).orElse(null);
        LeadEntity lead = leads.findById(r.leadId).orElse(null);
        return ReplyOut.of(r, campaignName, lead, dryRun);
    }

    public List<ReplyOut> list() {
        Map<String, String> names = new HashMap<>();
        for (CampaignEntity c : campaigns.findAll()) names.put(c.id, c.name);
        return replies.findAllByOrderByReceivedAtDesc(PageRequest.of(0, 500)).stream()
                .map(r -> ReplyOut.of(r, names.get(r.campaignId), leads.findById(r.leadId).orElse(null), null)).toList();
    }

    ReplyEntity mustGet(String id) { return replies.findById(id).orElseThrow(() -> ApiException.notFound("Reply not found")); }

    public ReplyOut patch(String id, ReplyPatch b) {
        ReplyEntity r = mustGet(id);
        if (b.handled() != null) r.handled = b.handled();
        if (b.unread() != null) r.unread = b.unread();
        if (b.draft() != null) r.draft = b.draft();
        return out(replies.save(r), null);
    }

    public ReplyOut answer(String id, AnswerRequest b) {
        ReplyEntity r = mustGet(id);
        try {
            OutboundMail.Delivery d = mail.answer(r, b.body().trim());
            return out(mustGet(id), d.dryRun());
        } catch (Exception e) {
            throw new ApiException(org.springframework.http.HttpStatus.BAD_GATEWAY, "The answer could not be sent: " + e.getMessage());
        }
    }

    public Map<String, Object> checkInbox() {
        imap.poll();
        return Map.of("ok", true, "imap", imap.enabled());
    }
}
