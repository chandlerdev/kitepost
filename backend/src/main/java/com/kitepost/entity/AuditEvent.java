package com.kitepost.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Who did what, when, from where. Written for sign-ins and every action that changes data or sends email. */
@Entity
@Table(name = "audit_events")
public class AuditEvent {
    @Id public String id;
    public Instant at;
    public String userId;
    public String userEmail;
    public String action;
    public String target;
    public String detail;
    public String ip;
}
