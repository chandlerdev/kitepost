package com.kitepost.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

public final class CurrentUser {
    private CurrentUser() {}

    public static Optional<AppUser> get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof AppUser u ? Optional.of(u) : Optional.empty();
    }

    public static Optional<String> id() { return get().map(AppUser::id); }

    /** Client IP as seen through the proxy (server.forward-headers-strategy=framework). */
    public static String ip() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra)) return null;
        return ip(sra.getRequest());
    }

    public static String ip(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return ip == null ? null : ip.length() > 64 ? ip.substring(0, 64) : ip;
    }
}
