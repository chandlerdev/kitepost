package com.kitepost.controller;

import com.kitepost.dto.Dtos.Status;
import com.kitepost.service.ReferenceData;
import com.kitepost.service.StatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReferenceController {
    private final ReferenceData ref;
    private final StatusService status;

    public ReferenceController(ReferenceData ref, StatusService status) {
        this.ref = ref;
        this.status = status;
    }

    @GetMapping("/reference")
    public ReferenceData.Reference reference() { return ref.reference(); }

    @GetMapping("/status")
    public Status status() { return status.status(); }
}
