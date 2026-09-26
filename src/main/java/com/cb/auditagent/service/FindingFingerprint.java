package com.cb.auditagent.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.stream.Collectors;

final public class FindingFingerprint {
    private FindingFingerprint() {
    }

    public static String create(String ruleId, String filePath, String code) {
        String material = normalize(ruleId) + "\n" + normalizePath(filePath) + "\n" + normalizeCode(code);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String normalizePath(String value) {
        return normalize(value).replace((char) 92, '/');
    }

    private static String normalizeCode(String value) {
        return normalize(value).lines().map(String::trim).collect(Collectors.joining(" "));
    }
}
