package com.schwab.urlshortener.service;

import org.springframework.stereotype.Component;

/** base62(id) - DB identity already guarantees uniqueness, so no collision handling needed. */
@Component
public class CodeGenerator {

    private static final char[] ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final int BASE = ALPHABET.length;

    public String encode(long id) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be non-negative: " + id);
        }
        if (id == 0) {
            return String.valueOf(ALPHABET[0]);
        }
        StringBuilder sb = new StringBuilder();
        long n = id;
        while (n > 0) {
            sb.append(ALPHABET[(int) (n % BASE)]);
            n /= BASE;
        }
        return sb.reverse().toString();
    }
}
