package com.kitepost.security;

import com.kitepost.exception.ApiException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecurityRulesTest {
    @Test
    void passwordPolicy() {
        assertThrows(ApiException.class, () -> PasswordPolicy.check("short", "a@b.com"));
        assertThrows(ApiException.class, () -> PasswordPolicy.check("password1234", "a@b.com"));
        assertThrows(ApiException.class, () -> PasswordPolicy.check("priya-secret-2026", "priya@chairtime.com"));
        assertThrows(ApiException.class, () -> PasswordPolicy.check("aaaaaaaaaaaaaa", "a@b.com"));
        assertDoesNotThrow(() -> PasswordPolicy.check("Correct-Horse-Battery-9", "priya@chairtime.com"));
    }

    @Test
    void rateLimiter() {
        RateLimiter r = new RateLimiter(3, 60_000);
        assertTrue(r.tryAcquire("1.2.3.4"));
        assertTrue(r.tryAcquire("1.2.3.4"));
        assertTrue(r.tryAcquire("1.2.3.4"));
        assertFalse(r.tryAcquire("1.2.3.4"));
        assertTrue(r.tryAcquire("5.6.7.8"));
    }
}
