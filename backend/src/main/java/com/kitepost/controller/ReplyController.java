package com.kitepost.controller;

import com.kitepost.dto.Dtos.*;
import com.kitepost.service.ReplyService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/replies")
public class ReplyController {
    private final ReplyService replies;

    public ReplyController(ReplyService replies) { this.replies = replies; }

    @GetMapping
    public List<ReplyOut> list() { return replies.list(); }

    @PostMapping("/check")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public Map<String, Object> check() { return replies.checkInbox(); }

    /** Marking as read is allowed for everyone; changing "handled" or the draft needs an editor. */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR') or (#b.handled() == null and #b.draft() == null)")
    public ReplyOut patch(@PathVariable String id, @Valid @RequestBody ReplyPatch b) { return replies.patch(id, b); }

    @PostMapping("/{id}/answer")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ReplyOut answer(@PathVariable String id, @Valid @RequestBody AnswerRequest b) { return replies.answer(id, b); }
}
