package com.kitepost.security;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppUserJsonTest {
    @Test
    void sendsTheRealNameNotTheId() {
        AppUser u = new AppUser("u_abc123", "jane@example.com", "Jane Doe", "ADMIN", false);
        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode j = mapper.readTree(mapper.writeValueAsString(u));
        assertEquals("\"Jane Doe\"", j.get("name").toString());     // getName() (the id, for Spring Session) must not leak into "name"
        assertEquals("\"u_abc123\"", j.get("id").toString());
        assertEquals("u_abc123", u.getName());                       // the Principal name stays the id
    }
}
