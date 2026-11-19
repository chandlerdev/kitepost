package com.kitepost.controller;

import com.kitepost.dto.Dtos.*;
import com.kitepost.security.AppUser;
import com.kitepost.security.CurrentUser;
import com.kitepost.service.AuditService;
import com.kitepost.service.SessionKiller;
import com.kitepost.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Team management and the audit log. Admins only. */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final UserService users;
    private final AuditService audit;
    private final SessionKiller sessions;

    public AdminController(UserService users, AuditService audit, SessionKiller sessions) {
        this.users = users;
        this.audit = audit;
        this.sessions = sessions;
    }

    @GetMapping("/users")
    public List<UserOut> users() { return users.list(); }

    @PostMapping("/users")
    public UserOut create(@Valid @RequestBody UserCreateRequest b) { return users.create(b); }

    @PatchMapping("/users/{id}")
    public UserOut patch(@PathVariable String id, @Valid @RequestBody UserPatch b) {
        AppUser actor = CurrentUser.get().orElseThrow();
        UserOut out = users.patch(id, b, actor);
        if (!out.enabled()) sessions.endAll(id, null);
        return out;
    }

    @PostMapping("/users/{id}/reset-password")
    public UserOut resetPassword(@PathVariable String id, @Valid @RequestBody PasswordResetRequest b) {
        UserOut out = users.resetPassword(id, b);
        sessions.endAll(id, null);
        return out;
    }

    @GetMapping("/audit")
    public List<AuditOut> audit(@RequestParam(defaultValue = "200") int limit) {
        return audit.recent(limit).stream().map(AuditOut::of).toList();
    }
}
