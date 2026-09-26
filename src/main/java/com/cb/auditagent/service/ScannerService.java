package com.cb.auditagent.service;

import com.cb.auditagent.config.ScannerConfig;
import com.cb.auditagent.domain.Severity;
import com.cb.auditagent.domain.Vulnerability;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Service
public class ScannerService {
    private static final Logger logger = LoggerFactory.getLogger(ScannerService.class);
    private final ObjectMapper objectMapper;
    private final ManagedProcessRunner processRunner;
    private final ScannerConfig scannerConfig;
    private final MemoryRedactor redactor;
    private final String rulesDir = "rules";

    public ScannerService(ObjectMapper objectMapper, ManagedProcessRunner processRunner,
            ScannerConfig scannerConfig, MemoryRedactor redactor) {
        this.objectMapper = objectMapper;
        this.processRunner = processRunner;
        this.scannerConfig = scannerConfig;
        this.redactor = redactor;
    }

    public static class ScanResult {
        private final List<Vulnerability> findings;
        private final int filesScanned;
        private final int totalLoc;
        private final int errorCount;

        public ScanResult(List<Vulnerability> findings, int filesScanned, int totalLoc, int errorCount) {
            this.findings = findings;
            this.filesScanned = filesScanned;
            this.totalLoc = totalLoc;
            this.errorCount = errorCount;
        }

        public List<Vulnerability> getFindings() {
            return findings;
        }

        public int getFilesScanned() {
            return filesScanned;
        }

        public int getTotalLoc() {
            return totalLoc;
        }

        public int getErrorCount() {
            return errorCount;
        }
    }

    public ScanResult scan(String repoPath) {
        return scan(repoPath, null);
    }

    public ScanResult scan(String repoPath, Consumer<Map<String, Object>> progressCallback) {
        logger.info("--- Starting Optimized Semgrep scan on {} ---", repoPath);
        File targetDir = new File(repoPath).getAbsoluteFile();

        // 1. Detect languages in target repo
        if (progressCallback != null) {
            Map<String, Object> progress = new HashMap<>();
            progress.put("step", "detect_languages");
            progress.put("progress", 20);
            progress.put("message", "📂 Scanning project directories & detecting languages...");
            progressCallback.accept(progress);
        }
        List<String> detectedLangs = detectLanguages(targetDir);
        logger.info("Detected languages: {}", detectedLangs);

        // 2. Select rule configurations
        if (progressCallback != null) {
            Map<String, Object> progress = new HashMap<>();
            progress.put("step", "select_rules");
            progress.put("progress", 40);
            progress.put("message", "⚙️ Selecting compliance rule packs for: " + String.join(", ", detectedLangs));
            progressCallback.accept(progress);
        }
        List<String> targetConfigs = getRuleConfigs(detectedLangs);

        // 3. Run Semgrep and get raw output
        if (progressCallback != null) {
            Map<String, Object> progress = new HashMap<>();
            progress.put("step", "run_semgrep");
            progress.put("progress", 60);
            progress.put("message", "🚀 Running Semgrep security analysis engine (this may take a moment)...");
            progressCallback.accept(progress);
        }
        String rawJson = runSemgrep(targetDir.toPath(), targetConfigs, progressCallback);
        if (rawJson.isBlank()) {
            throw new ScanExecutionException("Semgrep returned an empty report.");
        }

        // 4. Parse results
        if (progressCallback != null) {
            Map<String, Object> progress = new HashMap<>();
            progress.put("step", "parse_report");
            progress.put("progress", 85);
            progress.put("message", "📊 Parsing vulnerability report & mapping findings...");
            progressCallback.accept(progress);
        }
        return parseSemgrepReport(rawJson, targetDir.getAbsolutePath());
    }

    private static final Map<String, String> EXT_TO_LANG = new HashMap<>();
    static {
        EXT_TO_LANG.put(".java", "java");
        EXT_TO_LANG.put(".py", "python");
        EXT_TO_LANG.put(".js", "javascript");
        EXT_TO_LANG.put(".ts", "typescript");
        EXT_TO_LANG.put(".html", "html");
        EXT_TO_LANG.put(".htm", "html");
        EXT_TO_LANG.put(".kt", "kotlin"); // Updated from java
        EXT_TO_LANG.put(".jsx", "javascript");
        EXT_TO_LANG.put(".tsx", "typescript");

        // Expanded rules support
        EXT_TO_LANG.put(".go", "go");
        EXT_TO_LANG.put(".cs", "csharp");
        EXT_TO_LANG.put(".rb", "ruby");
        EXT_TO_LANG.put(".rs", "rust");
        EXT_TO_LANG.put(".tf", "terraform");
        EXT_TO_LANG.put(".c", "c");
        EXT_TO_LANG.put(".h", "c");
        EXT_TO_LANG.put(".json", "json");
        EXT_TO_LANG.put(".scala", "scala");
        EXT_TO_LANG.put(".swift", "swift");
        EXT_TO_LANG.put(".yaml", "yaml");
        EXT_TO_LANG.put(".yml", "yaml");
        EXT_TO_LANG.put(".sh", "bash");
        EXT_TO_LANG.put(".bash", "bash");
    }

    public List<String> detectLanguages(File repoDir) {
        Set<String> foundLangs = new HashSet<>();
        Set<String> excludeDirs = new HashSet<>(Arrays.asList(
                ".git", "node_modules", "__pycache__", ".venv", "venv",
                "build", "dist", "target", "bin", "obj", "out", ".gradle", ".m2"));

        traverseForLanguages(repoDir, excludeDirs, foundLangs);
        return new ArrayList<>(foundLangs);
    }

    public String detectLanguageForFile(String filename) {
        if (filename == null)
            return null;
        if (filename.equalsIgnoreCase("Dockerfile"))
            return "dockerfile";

        int idx = filename.lastIndexOf('.');
        if (idx != -1) {
            String ext = filename.substring(idx).toLowerCase();
            return EXT_TO_LANG.get(ext);
        }
        return null;
    }

    private void traverseForLanguages(File dir, Set<String> excludeDirs, Set<String> foundLangs) {
        File[] files = dir.listFiles();
        if (files == null)
            return;

        for (File file : files) {
            if (file.isDirectory()) {
                if (!excludeDirs.contains(file.getName())) {
                    traverseForLanguages(file, excludeDirs, foundLangs);
                }
            } else {
                String lang = detectLanguageForFile(file.getName());
                if (lang != null) {
                    foundLangs.add(lang);
                }
            }
        }
    }

    public List<String> getRuleConfigs(List<String> languages) {
        List<String> configs = new ArrayList<>();
        File baseRules = new File(rulesDir).getAbsoluteFile();
        for (String lang : languages) {
            File langDir = new File(baseRules, lang);
            if (langDir.exists() && langDir.isDirectory()) {
                configs.add(langDir.getAbsolutePath());
            }
        }
        if (configs.isEmpty()) {
            configs.add(baseRules.getAbsolutePath());
        }
        return configs;
    }

    public ScannerConfig getConfig() {
        return scannerConfig;
    }

    private String runSemgrep(Path repoPath, List<String> configs,
            Consumer<Map<String, Object>> progressCallback) {
        List<String> cmd = new ArrayList<>(Arrays.asList("semgrep", "scan"));
        for (String config : configs) {
            cmd.add("--config");
            cmd.add(config);
        }

        // Add standard excludes
        String[] excludePatterns = {
                "node_modules", ".venv", "venv", "__pycache__", ".git",
                "dist", "build", "target", "bin", "obj", "out",
                ".idea", ".vscode", ".gradle", ".m2",
                "*.min.js", "*.min.css", "package-lock.json", "yarn.lock",
                "static/js", "resources/static", "templates", "test", "tests"
        };
        for (String pattern : excludePatterns) {
            cmd.add("--exclude");
            cmd.add(pattern);
        }

        cmd.addAll(Arrays.asList(
                "--json",
                "--metrics=off",
                "--no-git-ignore",
                "--disable-version-check",
                "--max-target-bytes", String.valueOf(scannerConfig.getMaxTargetBytes()),
                "--max-memory", String.valueOf(scannerConfig.getMaxMemoryMb()),
                "--timeout", String.valueOf(scannerConfig.getRuleTimeoutSeconds()),
                "--timeout-threshold", String.valueOf(scannerConfig.getRuleTimeoutSeconds()),
                repoPath.toString()));

        logger.info("Executing command: {}", String.join(" ", cmd));

        Duration timeout = Duration.ofSeconds(scannerConfig.getTimeoutSeconds());
        Duration heartbeat = Duration.ofSeconds(scannerConfig.getHeartbeatSeconds());
        ManagedProcessRunner.ProcessResult result = processRunner.run(
                cmd, repoPath.toAbsolutePath().normalize(), timeout, heartbeat, elapsed -> {
                    if (progressCallback == null) {
                        return;
                    }
                    Map<String, Object> progress = new LinkedHashMap<>();
                    progress.put("step", "run_semgrep");
                    progress.put("progress", 60);
                    progress.put("message", "Semgrep is still running (" + formatElapsed(elapsed) + " elapsed).");
                    progressCallback.accept(progress);
                });

        logger.info("Semgrep process exited with code {}", result.exitCode());
        if (result.exitCode() == 0 || result.exitCode() == 1) {
            if (!result.stdout().isBlank()) {
                return result.stdout();
            }
            logger.warn("Semgrep returned exit code {} but stdout is blank, falling through to error handling",
                    result.exitCode());
        }

        String diagnostic = redactor.redactAndCap(result.stderr().strip(), 1_000);
        String message = "Semgrep exited with code " + result.exitCode() + ".";
        if (!diagnostic.isBlank()) {
            message += "\nStderr: " + diagnostic;
        }
        throw new ScanExecutionException(message);
    }

    private ScanResult parseSemgrepReport(String rawJson, String repoAbsolutePath) {
        List<Vulnerability> findings = new ArrayList<>();
        int filesScanned = 0;
        int totalLoc = 0;
        int errorCount = 0;

        try {
            JsonNode root = objectMapper.readTree(rawJson);
            if (root == null || !root.isObject() || !root.has("results") || !root.get("results").isArray()) {
                throw new IllegalArgumentException("Semgrep JSON report does not contain a results array");
            }

            // 1. Get scanned paths metadata
            if (root.has("paths") && root.get("paths").has("scanned")) {
                JsonNode scannedNode = root.get("paths").get("scanned");
                filesScanned = scannedNode.size();

                // Estimate LOC as fallback
                for (JsonNode pathNode : scannedNode) {
                    String pathStr = pathNode.asText();
                    File file = new File(pathStr);
                    if (file.exists() && file.isFile()) {
                        try (Stream<String> lines = Files.lines(file.toPath(), StandardCharsets.UTF_8)) {
                            totalLoc += lines.count();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            // 2. Overwrite total LOC if Semgrep stats are available
            if (root.has("interprint_stats") && root.get("interprint_stats").has("total_lines_scanned")) {
                totalLoc = root.get("interprint_stats").get("total_lines_scanned").asInt();
            } else if (root.has("stats") && root.get("stats").has("total_lines_scanned")) {
                totalLoc = root.get("stats").get("total_lines_scanned").asInt();
            }

            // 3. Count scanner errors
            if (root.has("errors")) {
                errorCount = root.get("errors").size();
                if (errorCount > 0) {
                    logger.warn("Semgrep completed with {} parser/syntax warnings or errors.", errorCount);
                }
            }

            // 4. Parse findings (results)
            if (root.has("results")) {
                JsonNode results = root.get("results");
                for (JsonNode item : results) {
                    String checkId = item.get("check_id").asText();
                    String path = item.get("path").asText();
                    int line = item.get("start").get("line").asInt();

                    // Make path relative to target repo path
                    String relPath = path;
                    try {
                        File repoDirFile = new File(repoAbsolutePath);
                        File matchFile = new File(path);
                        if (matchFile.isAbsolute()
                                && matchFile.getAbsolutePath().startsWith(repoDirFile.getAbsolutePath())) {
                            relPath = repoDirFile.toURI().relativize(matchFile.toURI()).getPath();
                        }
                    } catch (Exception e) {
                        logger.warn("Could not make path relative: " + path, e);
                    }

                    // Extract exact snippet for stable fingerprinting
                    String exactSnippet = "";
                    if (item.has("extra") && item.get("extra").has("lines")) {
                        exactSnippet = item.get("extra").get("lines").asText().trim();
                    }

                    // Extract rich code context snippet for the LLM and UI
                    String codeSnippet = readContextFromFile(path, line);
                    if (codeSnippet == null || codeSnippet.isEmpty()
                            || codeSnippet.startsWith("Source code not available")
                            || codeSnippet.equals("Source code could not be read")) {
                        codeSnippet = exactSnippet;
                    }
                    if (codeSnippet == null || codeSnippet.isEmpty()) {
                        codeSnippet = "Source code not available";
                    }

                    // Severity Mapping
                    Severity severity = Severity.MEDIUM;
                    if (item.has("extra") && item.get("extra").has("severity")) {
                        String sevStr = item.get("extra").get("severity").asText().toUpperCase();
                        if ("ERROR".equals(sevStr)) {
                            severity = Severity.HIGH;
                        } else if ("WARNING".equals(sevStr)) {
                            severity = Severity.MEDIUM;
                        } else if ("INFO".equals(sevStr)) {
                            severity = Severity.LOW;
                        }
                    }

                    // Vulnerability Type (last segment of check_id)
                    String vulnType = checkId;
                    int lastDot = checkId.lastIndexOf('.');
                    if (lastDot != -1 && lastDot < checkId.length() - 1) {
                        vulnType = checkId.substring(lastDot + 1);
                    }

                    String description = "";
                    if (item.has("extra") && item.get("extra").has("message")) {
                        description = item.get("extra").get("message").asText();
                    }

                    String language = detectLanguageFromExtension(path);

                    String uniqueId = "VULN-"
                            + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

                    Vulnerability vuln = new Vulnerability(
                            uniqueId, relPath, line, codeSnippet, severity, vulnType, description, language);
                    vuln.setRuleId(checkId);
                    String fingerprint = FindingFingerprint.create(checkId, relPath, exactSnippet);
                    vuln.setFindingFingerprint(fingerprint);

                    // Deduplicate by fingerprint to treat exact same vulnerable code in the same
                    // file as a single finding
                    if (findings.stream().noneMatch(f -> fingerprint.equals(f.getFindingFingerprint()))) {
                        findings.add(vuln);
                    }
                }
            }

        } catch (Exception e) {
            throw new ScanExecutionException("Semgrep returned an invalid JSON report.", e);
        }

        return new ScanResult(findings, filesScanned, totalLoc, errorCount);
    }

    private String formatElapsed(Duration elapsed) {
        long totalSeconds = Math.max(0, elapsed.toSeconds());
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return minutes > 0 ? minutes + "m " + seconds + "s" : seconds + "s";
    }

    private String detectLanguageFromExtension(String filePath) {
        String name = filePath.toLowerCase();
        if (name.endsWith(".java") || name.endsWith(".kt"))
            return "Java";
        if (name.endsWith(".py"))
            return "Python";
        if (name.endsWith(".js") || name.endsWith(".jsx"))
            return "JavaScript";
        if (name.endsWith(".ts") || name.endsWith(".tsx"))
            return "TypeScript";
        if (name.endsWith(".cs"))
            return "C#";
        if (name.endsWith(".go"))
            return "Go";
        if (name.endsWith(".rb"))
            return "Ruby";
        if (name.endsWith(".php"))
            return "PHP";
        if (name.endsWith(".rs"))
            return "Rust";
        return "Unknown";
    }

    private String readContextFromFile(String filePath, int lineNumber) {
        try {
            File file = new File(filePath);
            if (!file.exists())
                return "Source code not available";

            List<String> lines;
            try {
                lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            } catch (java.nio.charset.MalformedInputException e) {
                try {
                    lines = Files.readAllLines(file.toPath(), StandardCharsets.ISO_8859_1);
                } catch (Exception inner) {
                    return "Source code not available (unreadable encoding)";
                }
            }
            int exactLineIdx = lineNumber - 1;
            if (exactLineIdx >= 0 && exactLineIdx < lines.size()) {
                int startIdx = Math.max(0, exactLineIdx - 10);
                int endIdx = Math.min(exactLineIdx + 11, lines.size()); // +11 to include the 10th line after
                StringBuilder sb = new StringBuilder();
                for (int i = startIdx; i < endIdx; i++) {
                    sb.append(lines.get(i)).append("\n");
                }
                return sb.toString().stripTrailing();
            }
        } catch (Exception e) {
            logger.warn("Error reading context from {} at line {}", filePath, lineNumber, e);
        }
        return "Source code could not be read";
    }
}
