package com.cb.auditagent.service.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry holding available SecurityScanner strategy implementations.
 * Enables Open/Closed (OCP) extension of scanner engines without modifying orchestrator controllers.
 */
@Component
public class ScannerRegistry {

    private static final Logger logger = LoggerFactory.getLogger(ScannerRegistry.class);
    private final Map<String, SecurityScanner> scannerMap = new ConcurrentHashMap<>();

    public ScannerRegistry(List<SecurityScanner> scanners) {
        if (scanners != null) {
            for (SecurityScanner scanner : scanners) {
                registerScanner(scanner);
            }
        }
    }

    public void registerScanner(SecurityScanner scanner) {
        if (scanner != null && scanner.getScannerName() != null) {
            String key = scanner.getScannerName().toLowerCase(Locale.ROOT).trim();
            scannerMap.put(key, scanner);
            logger.info("Registered security scanner: '{}' ({})", key, scanner.getClass().getSimpleName());
        }
    }

    public SecurityScanner getScanner(String scannerName) {
        if (scannerName == null || scannerName.isBlank()) {
            return getDefaultScanner();
        }
        String key = scannerName.toLowerCase(Locale.ROOT).trim();
        SecurityScanner scanner = scannerMap.get(key);
        if (scanner == null) {
            logger.warn("Scanner '{}' not found in registry. Falling back to default scanner.", scannerName);
            return getDefaultScanner();
        }
        return scanner;
    }

    public SecurityScanner getDefaultScanner() {
        SecurityScanner defaultScanner = scannerMap.get("semgrep");
        if (defaultScanner != null) {
            return defaultScanner;
        }
        return scannerMap.values().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No SecurityScanner implementations registered in ScannerRegistry"));
    }

    public Collection<SecurityScanner> getAllScanners() {
        return Collections.unmodifiableCollection(scannerMap.values());
    }
}
