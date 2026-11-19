package com.kitepost.security;

import com.kitepost.config.Json;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

final class JsonResponses {
    private JsonResponses() {}

    static void write(HttpServletResponse res, int status, String message, String code) throws IOException {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", message);
        if (code != null) body.put("code", code);
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write(Json.write(body));
    }
}
