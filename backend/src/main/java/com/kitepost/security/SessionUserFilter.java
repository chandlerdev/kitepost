package com.kitepost.security;

import com.kitepost.entity.UserAccount;
import com.kitepost.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Re-checks the signed-in user against the database on every request, so that
 * disabling a user or changing their role takes effect immediately, and blocks
 * everything except /api/auth/** until a required password change is done.
 */
public class SessionUserFilter extends OncePerRequestFilter {
    private final UserRepository users;
    private final SecurityContextRepository contexts;

    public SessionUserFilter(UserRepository users, SecurityContextRepository contexts) {
        this.users = users;
        this.contexts = contexts;
    }

    public static Authentication authenticationFor(AppUser u) {
        return UsernamePasswordAuthenticationToken.authenticated(u, null, List.of(new SimpleGrantedAuthority("ROLE_" + u.role())));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof AppUser current) {
            Optional<UserAccount> account = users.findById(current.id());
            if (account.isEmpty() || !account.get().enabled) {
                HttpSession s = req.getSession(false);
                if (s != null) s.invalidate();
                SecurityContextHolder.clearContext();
                JsonResponses.write(res, 401, "Your account has been disabled. Ask an admin.", "signed_out");
                return;
            }
            AppUser fresh = AppUser.of(account.get());
            if (!fresh.equals(current)) {
                SecurityContext ctx = SecurityContextHolder.createEmptyContext();
                ctx.setAuthentication(authenticationFor(fresh));
                SecurityContextHolder.setContext(ctx);
                contexts.saveContext(ctx, req, res);
            }
            String path = req.getRequestURI();
            if (fresh.mustChangePassword() && !path.startsWith("/api/auth/")) {
                JsonResponses.write(res, 403, "Choose a new password before you continue.", "password_change_required");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
