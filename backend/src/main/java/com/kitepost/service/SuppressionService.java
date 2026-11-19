package com.kitepost.service;

import com.kitepost.entity.Ids;
import com.kitepost.entity.SuppressionEntity;
import com.kitepost.repository.SuppressionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/** Do-not-contact list. Checked before drafting, before approving and again right before sending. */
@Service
public class SuppressionService {
    private final SuppressionRepository repo;

    public SuppressionService(SuppressionRepository repo) { this.repo = repo; }

    public boolean isSuppressed(String email) {
        if (email == null || email.isBlank()) return false;
        String e = email.toLowerCase(Locale.ROOT).trim();
        String dom = e.contains("@") ? e.substring(e.indexOf('@') + 1) : e;
        return repo.existsByValueIn(List.of(e, dom));
    }

    public void suppress(String value, String reason) {
        String v = value.toLowerCase(Locale.ROOT).trim();
        if (v.length() > 320) v = v.substring(0, 320);
        if (repo.existsById(v)) return;
        SuppressionEntity s = new SuppressionEntity();
        s.value = v;
        s.reason = reason.length() > 100 ? reason.substring(0, 100) : reason;
        s.at = Ids.now();
        repo.save(s);
    }

    public void remove(String value) { repo.deleteById(value.toLowerCase(Locale.ROOT).trim()); }

    public List<SuppressionEntity> list() { return repo.findAllByOrderByAtDesc(); }
}
