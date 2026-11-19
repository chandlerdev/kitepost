package com.kitepost.service.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Text {
    private Text() {}

    public static final int CI = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Set<String> STOP = Set.of(("a an the and or of for to in on with our your we us is are be that which who what this these those "
            + "from by at as it its their them they you i my new best top more most very really also into over under per via about").split(" "));
    private static final Pattern WORD = Pattern.compile("[a-zà-ÿ0-9&'-]{3,}", CI);

    /** Meaningful lower-case words (3+ letters, no stop words). */
    public static List<String> terms(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        Matcher m = WORD.matcher(s.toLowerCase(Locale.ROOT));
        while (m.find()) if (!STOP.contains(m.group())) out.add(m.group());
        return out;
    }

    public static String cut(String s, int n) { return s == null ? null : s.length() > n ? s.substring(0, n) : s; }

    public static String capitalize(String s) { return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    public static boolean blank(String s) { return s == null || s.isBlank(); }
}
