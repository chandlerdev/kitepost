package com.kitepost.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "activity")
public class ActivityEntity {
    @Id public String id;
    public String campaignId;
    public String kind;
    public String text;
    public String leadId;
    public Instant at;
}
