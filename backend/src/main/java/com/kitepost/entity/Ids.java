package com.kitepost.entity;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;

public final class Ids {
    private static final SecureRandom RANDOM = new SecureRandom();
    private Ids() {}

    /** e.g. "l_3f9a1c2b7d4e" */
    public static String next(String prefix) {
        byte[] b = new byte[6];
        RANDOM.nextBytes(b);
        return prefix + "_" + HexFormat.of().formatHex(b);
    }

    public static Instant now() { return Instant.now(); }
}
