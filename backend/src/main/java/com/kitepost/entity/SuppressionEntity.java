package com.kitepost.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Do-not-contact list: an email address or a whole domain. */
@Entity
@Table(name = "suppression")
public class SuppressionEntity {
    @Id public String value;
    public String reason;
    public Instant at;
}
