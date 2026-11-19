package com.kitepost.security;

import com.kitepost.exception.ApiException;
import java.util.Locale;
import java.util.Set;

/** At least 12 characters, not a common password, not built from the email address. */
public final class PasswordPolicy {
    private static final Set<String> COMMON = Set.of(
            "password1234", "123456789012", "qwertyuiop12", "letmein12345", "welcome12345", "administrator",
            "kitepost1234", "changeme1234", "passwordpassword", "iloveyou1234", "1234567890ab", "abc123456789");

    private PasswordPolicy() {}

    public static void check(String password, String email) {
        if (password == null || password.length() < 12) throw ApiException.bad("Use at least 12 characters for the password.");
        if (password.length() > 128) throw ApiException.bad("Use at most 128 characters for the password.");
        String lower = password.toLowerCase(Locale.ROOT);
        if (COMMON.contains(lower)) throw ApiException.bad("That password is too common. Choose another one.");
        if (email != null) {
            String local = email.toLowerCase(Locale.ROOT).split("@")[0];
            if (local.length() >= 4 && lower.contains(local)) throw ApiException.bad("Don't use your email address in the password.");
        }
        if (password.chars().distinct().count() < 5) throw ApiException.bad("Use a mix of different characters in the password.");
    }
}
