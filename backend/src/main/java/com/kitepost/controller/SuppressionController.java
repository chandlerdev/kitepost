package com.kitepost.controller;

import com.kitepost.dto.Dtos.SuppressionOut;
import com.kitepost.dto.Dtos.SuppressionRequest;
import com.kitepost.service.AuditService;
import com.kitepost.service.SuppressionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/suppression")
public class SuppressionController {
    private final SuppressionService suppression;
    private final AuditService audit;

    public SuppressionController(SuppressionService suppression, AuditService audit) {
        this.suppression = suppression;
        this.audit = audit;
    }

    @GetMapping
    public List<SuppressionOut> list() { return suppression.list().stream().map(SuppressionOut::of).toList(); }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public List<SuppressionOut> add(@Valid @RequestBody SuppressionRequest b) {
        suppression.suppress(b.value(), "added by you");
        audit.record("suppression.add", b.value(), null);
        return list();
    }

    /** Removing someone from the list means they can be emailed again, so only admins can. */
    @DeleteMapping("/{value}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<SuppressionOut> remove(@PathVariable String value) {
        suppression.remove(value);
        audit.record("suppression.remove", value, null);
        return list();
    }
}
