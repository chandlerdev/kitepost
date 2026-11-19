package com.kitepost.controller;

import com.kitepost.exception.ApiException;
import com.kitepost.dto.Dtos.*;
import com.kitepost.entity.Models.Strategy;
import com.kitepost.security.CurrentUser;
import com.kitepost.security.RateLimiter;
import com.kitepost.service.CampaignService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CampaignController {
    private final CampaignService campaigns;
    private final RateLimiter strategyLimit = new RateLimiter(20, 60_000);   // AI calls cost money

    public CampaignController(CampaignService campaigns) { this.campaigns = campaigns; }

    @PostMapping("/strategy")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public Strategy strategy(@Valid @RequestBody StrategyRequest b) {
        if (!strategyLimit.tryAcquire(CurrentUser.id().orElse("?"))) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Wait a minute and try again.");
        return campaigns.strategy(b);
    }

    @GetMapping("/campaigns")
    public List<CampaignOut> list() { return campaigns.list(); }

    @PostMapping("/campaigns")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public CampaignOut create(@Valid @RequestBody NewCampaignRequest b) { return campaigns.create(b); }

    @GetMapping("/campaigns/{id}")
    public CampaignOut get(@PathVariable String id) { return campaigns.detail(id); }

    @PatchMapping("/campaigns/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public CampaignOut patch(@PathVariable String id, @Valid @RequestBody CampaignPatch b) { return campaigns.patch(id, b); }

    @PostMapping("/campaigns/{id}/{action}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public CampaignOut action(@PathVariable String id, @PathVariable String action, @Valid @RequestBody(required = false) MoreRequest b) {
        return campaigns.action(id, action, b);
    }
}
