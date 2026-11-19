package com.kitepost.service;

import com.kitepost.exception.ApiException;
import com.kitepost.dto.Dtos.*;
import com.kitepost.config.AppProperties;
import com.kitepost.entity.Ids;
import com.kitepost.entity.UserAccount;
import com.kitepost.repository.UserRepository;
import com.kitepost.security.AppUser;
import com.kitepost.security.PasswordPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class UserService {
    /** A real hash of a random value: unknown emails take as long as wrong passwords. */
    private final String dummyHash;

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final AppProperties.Security cfg;

    public UserService(UserRepository users, PasswordEncoder encoder, AuditService audit, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.cfg = props.security();
        this.dummyHash = encoder.encode(java.util.UUID.randomUUID().toString());
    }

    static String norm(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    /** Checks the password and the lockout. Same message for unknown email and wrong password. */
    public UserAccount authenticate(String email, String password, String ip) {
        Optional<UserAccount> found = users.findByEmail(norm(email));
        if (found.isEmpty()) {
            encoder.matches(password, dummyHash);
            audit.record(null, norm(email), "auth.login.failed", null, "unknown email", ip);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
        }
        UserAccount u = found.get();
        Instant now = Instant.now();
        if (u.lockedUntil != null && u.lockedUntil.isAfter(now)) {
            long min = Math.max(1, Duration.between(now, u.lockedUntil).toMinutes() + 1);
            audit.record(u.id, u.email, "auth.login.locked", null, null, ip);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Too many failed attempts. Try again in " + min + " minute" + (min == 1 ? "" : "s") + ".");
        }
        if (!u.enabled || !encoder.matches(password, u.passwordHash)) {
            u.failedLogins++;
            if (u.failedLogins >= cfg.loginMaxFailures()) {
                u.lockedUntil = now.plus(Duration.ofMinutes(cfg.lockoutMinutes()));
                u.failedLogins = 0;
            }
            users.save(u);
            audit.record(u.id, u.email, "auth.login.failed", null, u.enabled ? "wrong password" : "disabled account", ip);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
        }
        u.failedLogins = 0;
        u.lockedUntil = null;
        u.lastLoginAt = now;
        if (encoder.upgradeEncoding(u.passwordHash)) u.passwordHash = encoder.encode(password);
        UserAccount saved = users.save(u);
        audit.record(u.id, u.email, "auth.login", null, null, ip);
        return saved;
    }

    public List<UserOut> list() { return users.findAllByOrderByCreatedAtAsc().stream().map(UserOut::of).toList(); }

    UserAccount mustGet(String id) { return users.findById(id).orElseThrow(() -> ApiException.notFound("User not found")); }

    public UserOut create(UserCreateRequest b) {
        String email = norm(b.email());
        if (users.findByEmail(email).isPresent()) throw ApiException.conflict("Someone with that email address already has an account.");
        PasswordPolicy.check(b.password(), email);
        UserAccount u = new UserAccount();
        u.id = Ids.next("u");
        u.email = email;
        u.name = b.name().trim();
        u.role = b.role();
        u.passwordHash = encoder.encode(b.password());
        u.enabled = true;
        u.mustChangePassword = true;      // the person picks their own password at first sign-in
        u.createdAt = Ids.now();
        users.save(u);
        audit.record("user.create", u.id, u.email + " as " + u.role);
        return UserOut.of(u);
    }

    public UserOut patch(String id, UserPatch b, AppUser actor) {
        UserAccount u = mustGet(id);
        boolean self = u.id.equals(actor.id());
        if (self && b.enabled() != null && !b.enabled()) throw ApiException.bad("You can't disable your own account.");
        if (self && b.role() != null && b.role() != UserAccount.Role.ADMIN) throw ApiException.bad("You can't remove your own admin role.");
        boolean wasActiveAdmin = u.role == UserAccount.Role.ADMIN && u.enabled;
        if (b.name() != null && !b.name().isBlank()) u.name = b.name().trim();
        if (b.role() != null) u.role = b.role();
        if (b.enabled() != null) { u.enabled = b.enabled(); if (u.enabled) { u.lockedUntil = null; u.failedLogins = 0; } }
        if (wasActiveAdmin && (u.role != UserAccount.Role.ADMIN || !u.enabled) && users.countByRoleAndEnabledTrue(UserAccount.Role.ADMIN) <= 1)
            throw ApiException.bad("Keep at least one active admin.");
        users.save(u);
        audit.record("user.update", u.id, u.email + ": role " + u.role + (u.enabled ? ", active" : ", disabled"));
        return UserOut.of(u);
    }

    public UserOut resetPassword(String id, PasswordResetRequest b) {
        UserAccount u = mustGet(id);
        PasswordPolicy.check(b.password(), u.email);
        u.passwordHash = encoder.encode(b.password());
        u.mustChangePassword = true;
        u.lockedUntil = null;
        u.failedLogins = 0;
        users.save(u);
        audit.record("user.password.reset", u.id, u.email);
        return UserOut.of(u);
    }

    public UserAccount changeOwnPassword(String userId, PasswordChangeRequest b) {
        UserAccount u = mustGet(userId);
        if (!encoder.matches(b.current(), u.passwordHash)) throw ApiException.bad("Your current password is incorrect.");
        if (b.current().equals(b.next())) throw ApiException.bad("Choose a password that's different from the current one.");
        PasswordPolicy.check(b.next(), u.email);
        u.passwordHash = encoder.encode(b.next());
        u.mustChangePassword = false;
        UserAccount saved = users.save(u);
        audit.record("auth.password.change", u.id, null);
        return saved;
    }
}
