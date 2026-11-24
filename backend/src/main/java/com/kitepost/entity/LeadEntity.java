package com.kitepost.entity;

import com.kitepost.entity.Models.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "leads")
public class LeadEntity {
    @Id public String id;
    public String campaignId;
    public String domain;
    public String url;
    public String company;
    public String country;
    public String location;
    public String query;
    public String snippet;
    public String status;            // analyzing | classified | review | no_email | suppressed | approved | sending | sent | replied | bounced | failed | rejected | skipped
    public String category;          // customer | partner | vendor | competitor | irrelevant
    public Double confidence;
    public Double score;
    public String reason;
    public String categorySource;    // claude | rules | you
    public String industry;
    public String title;
    public String siteSummary;
    public String lang;
    @Convert(converter = JsonConverters.StringList.class) public List<String> emails;
    @Convert(converter = JsonConverters.StringList.class) public List<String> pages;
    public String email;
    public String phone;             // contact number: Maps listing first, else read from the website
    @Convert(converter = JsonConverters.DraftJson.class) public Draft draft;
    @Convert(converter = JsonConverters.SentJson.class) public SentInfo sent;
    public String sentMessageId;
    public Instant approvedAt;
    public String error;
    public String contactFirst;
    public String templateId;        // the campaign template chosen in Review
    public Instant openedAt;         // first time the tracking image loaded (likely opened)
    public int openCount;
    public boolean openSeen = true;  // false until the Notifications page has been viewed
    public Instant createdAt;
    public Instant updatedAt;
    @Version public Long version;
}
