package com.kitepost.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** A person who can sign in. Passwords are stored only as bcrypt hashes. */
@Entity
@Table(name = "users")
public class UserAccount {
    public enum Role { ADMIN, EDITOR, VIEWER }

    @Id public String id;
    public String email;          // always lower-case
    public String name;
    public String passwordHash;
    @Enumerated(EnumType.STRING) public Role role;
    public boolean enabled = true;
    public boolean mustChangePassword;
    public int failedLogins;
    public Instant lockedUntil;
    public Instant lastLoginAt;
    public Instant createdAt;
    @Version public Long version;
}
