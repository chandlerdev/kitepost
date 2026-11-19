package com.kitepost.config;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * One shared Jackson 3 mapper for code that runs outside Spring's HTTP layer
 * (JPA converters, reference files, Claude and search responses).
 */
public final class Json {
    public static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    public static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};
    public static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private Json() {}

    public static String write(Object value) { return MAPPER.writeValueAsString(value); }

    public static <T> T read(String json, Class<T> type) { return MAPPER.readValue(json, type); }

    public static <T> T read(String json, TypeReference<T> type) { return MAPPER.readValue(json, type); }

    /** Parses the first {...} object in a text (used for model answers). */
    public static Map<String, Object> firstObject(String text) {
        int start = text.indexOf('{'), end = text.lastIndexOf('}');
        if (start < 0 || end < start) throw new IllegalArgumentException("No JSON object in the answer");
        return read(text.substring(start, end + 1), MAP);
    }

    // small helpers for reading loosely typed maps
    public static String str(Map<String, ?> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public static double num(Map<String, ?> m, String key, double fallback) {
        Object v = m == null ? null : m.get(key);
        if (v instanceof Number n) return n.doubleValue();
        try { return v == null ? fallback : Double.parseDouble(String.valueOf(v)); } catch (NumberFormatException e) { return fallback; }
    }

    @SuppressWarnings("unchecked")
    public static List<String> strings(Map<String, ?> m, String key) {
        Object v = m == null ? null : m.get(key);
        if (!(v instanceof List<?> list)) return List.of();
        return ((List<Object>) list).stream().filter(x -> x != null).map(String::valueOf).toList();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Map<String, ?> m, String key) {
        Object v = m == null ? null : m.get(key);
        return v instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
