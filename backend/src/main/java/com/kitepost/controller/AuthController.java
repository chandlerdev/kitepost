package com.kitepost.controller;

import com.kitepost.exception.ApiException;
import com.kitepost.dto.Dtos.LoginRequest;
import com.kitepost.dto.Dtos.PasswordChangeRequest;
import com.kitepost.config.AppProperties;
import com.kitepost.entity.UserAccount;
import com.kitepost.security.AppUser;
import com.kitepost.security.CurrentUser;
import com.kitepost.security.RateLimiter;
import com.kitepost.security.SessionUserFilter;
import com.kitepost.service.AuditService;
import com.kitepost.service.SessionKiller;
import com.kitepost.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService users;
    private final SecurityContextRepository contexts;
    private final CookieCsrfTokenRepository csrf;
    private final AuditService audit;
    private final SessionKiller sessions;
    private final RateLimiter byIp, byEmail;

    public AuthController(UserService users, SecurityContextRepository contexts, CookieCsrfTokenRepository csrf, AuditService audit,
                          SessionKiller sessions, AppProperties props) {
        this.users = users;
        this.contexts = contexts;
        this.csrf = csrf;
        this.audit = audit;
        this.sessions = sessions;
        int perMinute = props.security().loginAttemptsPerMinute();
        this.byIp = new RateLimiter(perMinute, 60_000);
        this.byEmail = new RateLimiter(perMinute, 60_000);
    }

    /** Loads the XSRF-TOKEN cookie. Call on page load and after sign-in or sign-out. */
    @GetMapping("/csrf")
    public Map<String, Object> csrf() { return Map.of("ok", true); }

    @GetMapping("/me")
    public Map<String, Object> me() {
        return Map.of("user", CurrentUser.get().orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to continue.")));
    }

    private void signIn(AppUser user, HttpServletRequest req, HttpServletResponse res) {
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(SessionUserFilter.authenticationFor(user));
        SecurityContextHolder.setContext(ctx);
        contexts.saveContext(ctx, req, res);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest b, HttpServletRequest req, HttpServletResponse res) {
        String ip = CurrentUser.ip(req);
        if (!byIp.tryAcquire(ip) || !byEmail.tryAcquire(b.email().trim().toLowerCase(Locale.ROOT)))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many sign-in attempts. Wait a minute and try again.");
        UserAccount u = users.authenticate(b.email(), b.password(), ip);
        HttpSession old = req.getSession(false);
        if (old != null) old.invalidate();          // new session id after sign-in (no session fixation)
        req.getSession(true);
        AppUser user = AppUser.of(u);
        signIn(user, req, res);
        csrf.saveToken(null, req, res);             // new CSRF token after sign-in; the next request gets it
        return Map.of("user", user);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest req, HttpServletResponse res) {
        CurrentUser.get().ifPresent(u -> audit.record("auth.logout", null, null));
        HttpSession s = req.getSession(false);
        if (s != null) s.invalidate();              // deletes the session from the database
        SecurityContextHolder.clearContext();
        csrf.saveToken(null, req, res);
        return Map.of("ok", true);
    }

    @PostMapping("/password")
    public Map<String, Object> changePassword(@Valid @RequestBody PasswordChangeRequest b, HttpServletRequest req, HttpServletResponse res) {
        AppUser current = CurrentUser.get().orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to continue."));
        UserAccount u = users.changeOwnPassword(current.id(), b);
        req.changeSessionId();
        AppUser user = AppUser.of(u);
        signIn(user, req, res);
        sessions.endAll(u.id, req.getSession().getId());   // sign out everywhere else
        return Map.of("user", user);
    }
}
