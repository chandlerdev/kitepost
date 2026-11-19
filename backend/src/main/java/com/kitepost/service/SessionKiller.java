package com.kitepost.service;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Ends a user's sessions on every device (after a password change, reset or disable). */
@Component
public class SessionKiller {
    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionKiller(FindByIndexNameSessionRepository<? extends Session> sessions) { this.sessions = sessions; }

    /** @param keepSessionId the current session to keep, or null to end all */
    public int endAll(String userId, String keepSessionId) {
        Set<String> ids = sessions.findByPrincipalName(userId).keySet();
        int n = 0;
        for (String id : ids) {
            if (id.equals(keepSessionId)) continue;
            sessions.deleteById(id);
            n++;
        }
        return n;
    }
}
