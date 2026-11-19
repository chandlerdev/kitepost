package com.kitepost.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecretBoxTest {
    @Test
    void roundTripsAndNeverStoresPlainText() {
        SecretBox box = new SecretBox("a-long-random-key-for-the-tests-1234567890");
        String enc = box.encrypt("My$ecret pass word");
        assertTrue(enc.startsWith("v1:"));
        assertFalse(enc.contains("ecret"));
        assertEquals("My$ecret pass word", box.decrypt(enc));
        assertNotEquals(enc, box.encrypt("My$ecret pass word"), "a fresh IV every time");
    }

    @Test
    void wrongKeyOrTamperedValueIsNotReadable() {
        SecretBox a = new SecretBox("key-one-key-one-key-one-key-one-0000");
        SecretBox b = new SecretBox("key-two-key-two-key-two-key-two-0000");
        String enc = a.encrypt("secret");
        assertNull(b.decrypt(enc));
        assertNull(a.decrypt(enc.substring(0, enc.length() - 4) + "AAAA"));
        assertNull(a.decrypt("not-encrypted"));
    }

    @Test
    void emptyIsEmptyAndMissingKeyRefusesToEncrypt() {
        SecretBox none = new SecretBox("");
        assertFalse(none.available());
        assertEquals("", none.encrypt(""));
        assertEquals("", none.decrypt(""));
        assertThrows(IllegalStateException.class, () -> none.encrypt("x"));
        assertNull(none.decrypt("v1:AAAA"));
    }
}
