package com.cb.auditagent.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.cb.auditagent.domain.Vulnerability;

final class SecurityConceptCatalog {
    static final int VERSION = 1;

    record Concept(String category, String framework, List<String> aliases) {
    }

    private SecurityConceptCatalog() {
    }

    static Concept describe(Vulnerability vulnerability) {
        String text = combined(vulnerability).toLowerCase(Locale.ROOT);
        if (containsAny(text, "sql injection", "sqli", "tainted-sql", "jdbc-sql")) {
            return new Concept("INJECTION", framework(text), List.of(
                    "sql injection", "sqli", "query concatenation", "parameterized query", "prepared statement"));
        }
        if (containsAny(text, "cross-site scripting", "xss", "dangerouslysetinnerhtml", "html-string")) {
            return new Concept("XSS", framework(text), List.of(
                    "cross site scripting", "xss", "output encoding", "html escaping", "sanitization"));
        }
        if (containsAny(text, "path traversal", "directory traversal", "tainted-file-path")) {
            return new Concept("PATH_TRAVERSAL", framework(text), List.of(
                    "path traversal", "directory traversal", "canonical path", "allowlisted path"));
        }
        if (containsAny(text, "command injection", "system command", "runtime.exec", "processbuilder")) {
            return new Concept("COMMAND_INJECTION", framework(text), List.of(
                    "command injection", "shell execution", "process builder", "argument allowlist"));
        }
        if (containsAny(text, "deserialization", "objectinputstream", "pickle")) {
            return new Concept("DESERIALIZATION", framework(text), List.of(
                    "unsafe deserialization", "object input stream", "type allowlist", "untrusted object"));
        }
        if (containsAny(text, "csrf", "cross-site request forgery")) {
            return new Concept("CSRF", framework(text), List.of(
                    "csrf", "cross site request forgery", "csrf token", "same site cookie"));
        }
        if (containsAny(text, "xxe", "external entity", "doctype")) {
            return new Concept("XXE", framework(text), List.of(
                    "xml external entity", "xxe", "disable dtd", "external entities"));
        }
        return new Concept("OTHER", framework(text), List.of(
                Optional.ofNullable(vulnerability.getVulnType()).orElse("security vulnerability")));
    }

    static String buildSearchText(Vulnerability vulnerability, String summary, Concept concept) {
        List<String> parts = new ArrayList<>();
        parts.add("security-concept-catalog-v" + VERSION);
        parts.add(combined(vulnerability));
        parts.add(concept.category());
        parts.add(concept.framework());
        parts.addAll(concept.aliases());
        parts.add(Optional.ofNullable(summary).orElse(""));
        return String.join(" ", parts).replaceAll("\\s+", " ").trim();
    }

    static String buildQuery(Vulnerability vulnerability) {
        String raw = buildSearchText(vulnerability, "", describe(vulnerability));
        String normalized = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]+", " ")
                .replaceAll("\\s+", " ").trim();
        if (normalized.length() > 500)
            normalized = normalized.substring(0, 500);
        return normalized;
    }

    static String filePattern(String path) {
        if (path == null)
            return "";
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? "*" + path.substring(dot).toLowerCase(Locale.ROOT) : "";
    }

    static boolean matchesPattern(String pattern, String path) {
        if (pattern == null || path == null || !pattern.startsWith("*."))
            return false;
        return path.toLowerCase(Locale.ROOT).endsWith(pattern.substring(1).toLowerCase(Locale.ROOT));
    }

    private static String combined(Vulnerability v) {
        return String.join(" ",
                Optional.ofNullable(v.getRuleId()).orElse(""),
                Optional.ofNullable(v.getVulnType()).orElse(""),
                Optional.ofNullable(v.getDescription()).orElse(""),
                Optional.ofNullable(v.getLanguage()).orElse(""),
                Optional.ofNullable(v.getFilePath()).orElse(""));
    }

    private static String framework(String text) {
        if (text.contains("spring"))
            return "spring";
        if (text.contains("react"))
            return "react";
        if (text.contains("django"))
            return "django";
        if (text.contains("flask"))
            return "flask";
        if (text.contains("express"))
            return "express";
        if (text.contains("angular"))
            return "angular";
        return "";
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms)
            if (text.contains(term))
                return true;
        return false;
    }
}
