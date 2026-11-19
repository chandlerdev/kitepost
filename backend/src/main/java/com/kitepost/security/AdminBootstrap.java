package com.kitepost.security;

import com.kitepost.config.AppProperties;
import com.kitepost.entity.Ids;
import com.kitepost.entity.UserAccount;
import com.kitepost.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

/**
 * Creates the first admin when there are no users.
 * With ADMIN_EMAIL / ADMIN_PASSWORD set, those are used. Otherwise a random password is
 * generated and printed once in the log, and must be changed at first sign-in.
 */
@Component
@Order(1)
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AppProperties props;

    public AdminBootstrap(UserRepository users, PasswordEncoder encoder, AppProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) return;
        var s = props.security();
        String email = AppProperties.has(s.adminEmail()) ? s.adminEmail().trim().toLowerCase(Locale.ROOT) : "admin@kitepost.local";
        boolean generated = !AppProperties.has(s.adminPassword());
        String password = generated ? randomPassword() : s.adminPassword();
        if (!generated) PasswordPolicy.check(password, email);

        UserAccount u = new UserAccount();
        u.id = Ids.next("u");
        u.email = email;
        u.name = "Admin";
        u.passwordHash = encoder.encode(password);
        u.role = UserAccount.Role.ADMIN;
        u.enabled = true;
        u.mustChangePassword = generated;
        u.createdAt = Ids.now();
        users.save(u);

        if (generated) {
            log.warn("\n\n  First admin created.\n  Sign in with: {}\n  Password:     {}\n  You will be asked to choose a new password. This is shown only once.\n", email, password);
        } else {
            log.info("First admin created: {}", email);
        }
    }

    static String randomPassword() {
        byte[] b = new byte[18];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
