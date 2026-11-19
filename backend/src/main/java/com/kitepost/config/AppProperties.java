package com.kitepost.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything under "kitepost:" in application.properties (filled from environment variables). */
@ConfigurationProperties("kitepost")
public record AppProperties(
        boolean runWorkers,
        boolean seedSample,
        boolean simulateReplies,
        String publicUrl,
        Search search,
        Ai ai,
        Fetch fetch,
        Smtp smtp,
        Imap imap,
        Sending sending,
        Security security) {

    public record Search(String provider, String braveKey, String serpapiKey, String googleKey, String googleCx, int resultsPerQuery) {
        /** demo | brave | serpapi | google */
        public String effectiveProvider() {
            if (has(provider)) return provider.trim().toLowerCase();
            if (has(braveKey)) return "brave";
            if (has(serpapiKey)) return "serpapi";
            if (has(googleKey) && has(googleCx)) return "google";
            return "demo";
        }
    }

    public record Ai(String apiKey, String model) {
        public boolean enabled() { return has(apiKey); }
    }

    public record Fetch(int timeoutMs, String userAgent, int concurrency, boolean respectRobots) {}

    public record Smtp(String host, int port, boolean ssl, String user, String pass) {}

    public record Imap(String host, int port, boolean ssl, String user, String pass, String mailbox, int pollSeconds) {
        public boolean enabled() { return has(host); }
    }

    public record Sending(boolean dryRun, int intervalSeconds, int dailyLimit) {}

    public record Security(String adminEmail, String adminPassword, boolean cookieSecure,
                           int loginMaxFailures, int lockoutMinutes, int loginAttemptsPerMinute, String allowedOrigins) {}

    /** The public address of this app (https://example.com), used for the open-tracking image. Blank = no tracking. */
    public String trackingBase() { return has(publicUrl) ? publicUrl.trim().replaceAll("/+$", "") : null; }

    /** True when real SMTP sending is switched on. */
    public boolean smtpEnabled() { return has(smtp.host()) && !sending.dryRun(); }

    public static boolean has(String s) { return s != null && !s.isBlank(); }
}
