package com.kitepost.controller;

import com.kitepost.dto.Dtos.TemplateIn;
import com.kitepost.dto.Dtos.TemplateOut;
import com.kitepost.service.TemplateService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {
    private final TemplateService templates;

    public TemplateController(TemplateService templates) { this.templates = templates; }

    @GetMapping
    public List<TemplateOut> list(@RequestParam(required = false) String campaign) { return templates.list(campaign); }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public TemplateOut create(@Valid @RequestBody TemplateIn b) { return templates.create(b); }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public TemplateOut update(@PathVariable String id, @Valid @RequestBody TemplateIn b) { return templates.update(id, b); }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public void delete(@PathVariable String id) { templates.delete(id); }
}
