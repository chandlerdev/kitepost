package com.kitepost.controller;

import com.kitepost.dto.Dtos.MailIn;
import com.kitepost.dto.Dtos.MailOut;
import com.kitepost.dto.Dtos.MailTestOut;
import com.kitepost.service.mail.MailConfigService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Mail configuration in Settings. Admins only: it holds the mailbox logins. Passwords are never returned. */
@RestController
@RequestMapping("/api/settings/mail")
@PreAuthorize("hasRole('ADMIN')")
public class MailSettingsController {
    private final MailConfigService mail;

    public MailSettingsController(MailConfigService mail) { this.mail = mail; }

    @GetMapping
    public MailOut get() { return mail.view(); }

    @PutMapping
    public MailOut save(@Valid @RequestBody MailIn b) { return mail.save(b); }

    /** Forget the saved settings and use the server's (.env) again. */
    @DeleteMapping
    public MailOut reset() { return mail.reset(); }

    @PostMapping("/test/sending")
    public MailTestOut testSending() { return mail.testSending(); }

    @PostMapping("/test/replies")
    public MailTestOut testReplies() { return mail.testReplies(); }
}
