package com.kitepost.controller;

import com.kitepost.entity.Ids;
import com.kitepost.repository.LeadRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;

/**
 * The 1x1 image at the end of each sent email. It is public (the recipient's mail app loads it), so the
 * address carries a random token that only the sent email contains. Always answers with the image.
 */
@RestController
@RequestMapping("/api/track")
public class TrackingController {
    private static final byte[] PIXEL = Base64.getDecoder().decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7");
    private final LeadRepository leads;

    public TrackingController(LeadRepository leads) { this.leads = leads; }

    @GetMapping("/open/{leadId}/{token}.gif")
    public ResponseEntity<byte[]> open(@PathVariable String leadId, @PathVariable String token) {
        if (token.matches("[0-9a-f]{16}")) {
            leads.findById(leadId).ifPresent(l -> {
                if (l.sentMessageId != null && l.sentMessageId.startsWith("<kp." + token + "@")) leads.recordOpen(leadId, Ids.now());
            });
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_GIF).cacheControl(CacheControl.noStore()).body(PIXEL);
    }
}
