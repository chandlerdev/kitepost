package com.kitepost.service;

import com.kitepost.entity.AuditEvent;
import com.kitepost.entity.Ids;
import com.kitepost.repository.AuditRepository;
import com.kitepost.security.AppUser;
import com.kitepost.security.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditService {
    private final AuditRepository repo;

    public AuditService(AuditRepository repo) { this.repo = repo; }

    /** Records an action by the signed-in user. */
    public void record(String action, String target, String detail) {
        AppUser u = CurrentUser.get().orElse(null);
        record(u == null ? null : u.id(), u == null ? null : u.email(), action, target, detail, CurrentUser.ip());
    }

    public void record(String userId, String userEmail, String action, String target, String detail, String ip) {
        AuditEvent e = new AuditEvent();
        e.id = Ids.next("ev");
        e.at = Ids.now();
        e.userId = userId;
        e.userEmail = userEmail;
        e.action = cut(action, 60);
        e.target = cut(target, 300);
        e.detail = cut(detail, 1000);
        e.ip = ip;
        repo.save(e);
    }

    public List<AuditEvent> recent(int limit) { return repo.findAllByOrderByAtDesc(PageRequest.of(0, Math.min(Math.max(limit, 1), 500))); }

    static String cut(String s, int n) { return s == null ? null : s.length() > n ? s.substring(0, n) : s; }
}
