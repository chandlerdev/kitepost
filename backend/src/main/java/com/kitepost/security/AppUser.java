package com.kitepost.security;

import com.kitepost.entity.UserAccount;
import java.io.Serializable;
import java.security.Principal;

/**
 * The signed-in user, stored in the server-side session. Contains no secrets.
 * getName() returns the user id: Spring Session indexes sessions by it, so all of a
 * user's sessions can be ended (password change, reset, disable).
 */
public record AppUser(String id, String email, String name, String role, boolean mustChangePassword) implements Serializable, Principal {
    public static AppUser of(UserAccount u) {
        return new AppUser(u.id, u.email, u.name, u.role.name(), u.mustChangePassword);
    }
    @Override public String getName() { return id; }
    public boolean isAdmin() { return "ADMIN".equals(role); }
    public boolean canEdit() { return "ADMIN".equals(role) || "EDITOR".equals(role); }
}
