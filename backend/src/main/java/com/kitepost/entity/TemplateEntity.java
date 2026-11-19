package com.kitepost.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** An email template that belongs to one campaign. Placeholders: {company} {domain} {sender} {senderCompany} {role} {pitch}. */
@Entity
@Table(name = "templates")
public class TemplateEntity {
    @Id public String id;
    public String campaignId;
    public String name;
    public String type;        // friendly | formal | request | follow-up | other
    public String language;
    public String greeting;
    public String subject;
    public String body;
    public Instant createdAt;
    public Instant updatedAt;
    @Version public Long version;
}
