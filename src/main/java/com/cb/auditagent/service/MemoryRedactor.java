package com.cb.auditagent.service;

import org.springframework.stereotype.Service;

import com.cb.auditagent.config.MemoryConfig;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class MemoryRedactor {
    private static final String MASK = "[REDACTED]";
    private static final List<Pattern> SECRET_PATTERNS = List.of(
            Pattern.compile("AKIA[0-9A-Z]{16}"),
            Pattern.compile("(?i)(bearer\\s+)[a-z0-9._~+/=-]+"),
            Pattern.compile("(?i)((?:password|passwd|secret|token|api[_-]?key|access[_-]?key)\\s*[:=]\\s*)[^\\s,;]+"),
            Pattern.compile("(?s)-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----"));

    private final MemoryConfig config;

    public MemoryRedactor(MemoryConfig config) {
        this.config = config;
    }

    public String chat(String value) {
        return redactAndCap(value, config.getMaxChatChars());
    }

    public String tool(String value) {
        return redactAndCap(value, config.getMaxToolChars());
    }

    public String redactAndCap(String value, int maxChars) {
        String result = value == null ? "" : value;
        for (Pattern pattern : SECRET_PATTERNS)
            result = pattern.matcher(result).replaceAll(MASK);
        if (result.length() > maxChars) {
            String suffix = "\n...[truncated]";
            int contentLimit = Math.max(0, maxChars - suffix.length());
            result = result.substring(0, contentLimit) + suffix.substring(0, Math.min(suffix.length(), maxChars));
        }
        return result;
    }
}
