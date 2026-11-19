package com.kitepost.controller;

import com.kitepost.dto.Dtos.*;
import com.kitepost.service.LeadService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leads")
public class LeadController {
    private final LeadService leads;
    public LeadController(LeadService leads) { this.leads = leads; }

    @GetMapping
    public LeadPage list(@RequestParam(required = false) String campaign, @RequestParam(required = false) String category,
                         @RequestParam(required = false) String status, @RequestParam(required = false) String q,
                         @RequestParam(required = false) Integer limit) {
        return leads.list(campaign, category, status, q, limit);
    }

    @PostMapping("/approve")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ApproveManyOut approveMany(@Valid @RequestBody IdsRequest b) { return leads.approveMany(b.ids()); }

    @GetMapping("/{id}")
    public LeadOut get(@PathVariable String id) { return leads.get(id); }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public LeadOut patch(@PathVariable String id, @Valid @RequestBody LeadPatch b) { return leads.patch(id, b); }

    @PostMapping("/{id}/{action}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public LeadOut action(@PathVariable String id, @PathVariable String action) {
        return leads.action(id, action);
    }
}
