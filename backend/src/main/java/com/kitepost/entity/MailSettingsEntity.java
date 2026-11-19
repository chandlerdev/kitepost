package com.kitepost.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** The mail configuration saved in Settings (one row, id "main"). Passwords are kept encrypted. */
@Entity
@Table(name = "mail_settings")
public class MailSettingsEntity {
    public static final String ID = "main";

    @Id public String id = ID;
    public String smtpHost = "";
    public int smtpPort = 587;
    public boolean smtpSsl;
    public String smtpUser = "";
    public String smtpPassEnc = "";
    public String fromAddress = "";
    public boolean sendReal;
    public String imapHost = "";
    public int imapPort = 993;
    public boolean imapSsl = true;
    public String imapUser = "";
    public String imapPassEnc = "";
    public String imapMailbox = "INBOX";
    public Instant updatedAt;
    public String updatedBy;
    @Version public Long version;
}
