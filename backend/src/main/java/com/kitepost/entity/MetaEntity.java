package com.kitepost.entity;

import jakarta.persistence.*;

/** Small key/value settings: sends per day, first send time, IMAP position. */
@Entity
@Table(name = "meta")
public class MetaEntity {
    @Id public String k;
    public String v;

    public MetaEntity() {}
    public MetaEntity(String k, String v) { this.k = k; this.v = v; }
}
