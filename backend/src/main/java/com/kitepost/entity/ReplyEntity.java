package com.kitepost.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.kitepost.entity.Models.Answer;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "replies")
public class ReplyEntity {
    @Id public String id;
    public String leadId;
    public String campaignId;
    public String company;
    public String domain;
    public String lang;
    @JsonProperty("from") @Column(name = "from_address") public String fromAddress;
    public String fromName;
    public String subject;
    @JsonProperty("text") @Column(name = "body") public String body;
    public String messageId;
    public Instant receivedAt;
    public String intent;            // interested | question | not_interested | unsubscribe | out_of_office | bounce
    public String summary;
    public String translation;
    public String draft;
    public String analysisSource;
    public boolean unread = true;
    public boolean handled;
    public boolean simulated;
    @Convert(converter = JsonConverters.Answers.class) public List<Answer> answers = new ArrayList<>();
    @Version public Long version;
}
