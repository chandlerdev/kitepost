package com.kitepost.security;

import com.kitepost.config.AppProperties;
import com.kitepost.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** bcrypt with cost 12; the {bcrypt} prefix lets the algorithm be upgraded later. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(12)));
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository(AppProperties props) {
        CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse(); // the browser app reads it
        repo.setCookieName("KITEPOST-XSRF");   // not the default XSRF-TOKEN: other apps on the same domain use that name
        repo.setCookieCustomizer(c -> c.sameSite("Strict").secure(props.security().cookieSecure()).path("/"));
        return repo;
    }

    @Bean
    SecurityFilterChain api(HttpSecurity http, AppProperties props, UserRepository users,
                            SecurityContextRepository contexts, CookieCsrfTokenRepository csrfRepo) throws Exception {
        http
            .csrf(c -> c.csrfTokenRepository(csrfRepo).csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
            .securityContext(c -> c.securityContextRepository(contexts))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.GET, "/api/auth/csrf", "/actuator/health", "/api/track/open/*/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> JsonResponses.write(res, 401, "Sign in to continue.", "signed_out"))
                .accessDeniedHandler((req, res, ex) -> {
                    if (ex instanceof CsrfException) JsonResponses.write(res, 403, "Your security token expired. Reload the page and try again.", "csrf");
                    else JsonResponses.write(res, 403, "Your role doesn't allow this. Ask an admin.", "forbidden");
                }))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .logout(l -> l.disable())            // POST /api/auth/logout in AuthController
            .requestCache(r -> r.disable())
            .headers(h -> h
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                .frameOptions(f -> f.deny())
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", "camera=(), microphone=(), geolocation=()")))
            .addFilterBefore(new SessionUserFilter(users, contexts), AuthorizationFilter.class);

        List<String> origins = Arrays.stream(props.security().allowedOrigins().split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        if (origins.isEmpty()) {
            http.cors(c -> c.disable()); // the Next.js app proxies /api, so requests are same-origin
        } else {
            CorsConfiguration cfg = new CorsConfiguration();
            cfg.setAllowedOrigins(origins);
            cfg.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE"));
            cfg.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
            cfg.setAllowCredentials(true);
            UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
            src.registerCorsConfiguration("/api/**", cfg);
            http.cors(c -> c.configurationSource(src));
        }
        return http.build();
    }
}
