package com.cb.auditagent.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Prepares the environment used by tools executed against untrusted repository
 * contents. Provider credentials and process-injection variables are removed,
 * while homes and caches are kept inside the managed clone. A caller may supply
 * a validated temporary directory when a native tool requires a shorter path.
 */
final class ManagedProcessEnvironment {
    private static final Set<String> UNSAFE_EXACT_NAMES = Set.of(
            "BASH_ENV", "ENV", "PYTHONSTARTUP", "NODE_OPTIONS",
            "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS",
            "MAVEN_OPTS", "MAVEN_ARGS", "GRADLE_OPTS",
            "GIT_ASKPASS", "SSH_ASKPASS", "GIT_SSH_COMMAND",
            "LD_PRELOAD", "DYLD_INSERT_LIBRARIES");

    private ManagedProcessEnvironment() {
    }

    static void configure(ProcessBuilder builder, Path workspaceRoot) throws IOException {
        configure(builder, workspaceRoot, null);
    }

    private static String pythonUserSite = null;

    private static synchronized String getPythonUserSite() {
        if (pythonUserSite == null) {
            try {
                Process process = new ProcessBuilder("python3", "-m", "site", "--user-site").start();
                pythonUserSite = new String(process.getInputStream().readAllBytes()).trim();
            } catch (Exception e) {
                pythonUserSite = "";
            }
        }
        return pythonUserSite;
    }

    static void configure(ProcessBuilder builder, Path workspaceRoot, Path temporaryOverride) throws IOException {
        Path root = workspaceRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new IOException("Managed process workspace is not a real directory");
        }
        root = root.toRealPath();

        Path runtime = createContainedDirectory(root, root.resolve(".auditagent").resolve("runtime"));
        Path home = createContainedDirectory(root, runtime.resolve("home"));
        Path temporary = temporaryOverride == null
                ? createContainedDirectory(root, runtime.resolve("tmp"))
                : requireRealDirectory(temporaryOverride);
        Path gradle = createContainedDirectory(root, runtime.resolve("gradle"));
        Path xdgConfig = createContainedDirectory(root, runtime.resolve("config"));
        Path xdgCache = createContainedDirectory(root, runtime.resolve("cache"));

        String sitePackages = getPythonUserSite();

        Map<String, String> environment = builder.environment();
        environment.keySet().removeIf(ManagedProcessEnvironment::isSensitiveOrUnsafe);
        environment.put("HOME", home.toString());
        environment.put("USERPROFILE", home.toString());
        environment.put("TEMP", temporary.toString());
        environment.put("TMP", temporary.toString());
        environment.put("TMPDIR", temporary.toString());
        environment.put("GRADLE_USER_HOME", gradle.toString());
        environment.put("XDG_CONFIG_HOME", xdgConfig.toString());
        environment.put("XDG_CACHE_HOME", xdgCache.toString());
        environment.put("SEMGREP_SEND_METRICS", "off");
        environment.put("PYTHONUTF8", "1");

        if (sitePackages != null && !sitePackages.isEmpty()) {
            String existing = environment.get("PYTHONPATH");
            environment.put("PYTHONPATH", existing == null ? sitePackages : existing + ":" + sitePackages);
        }
    }

    private static Path requireRealDirectory(Path directory) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized)) {
            throw new IOException("Managed process temporary path is not a real directory");
        }
        return normalized.toRealPath();
    }

    static Path mavenRepository(Path workspaceRoot) {
        return workspaceRoot.toAbsolutePath().normalize()
                .resolve(".auditagent").resolve("runtime").resolve("home")
                .resolve(".m2").resolve("repository");
    }

    private static boolean isSensitiveOrUnsafe(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return UNSAFE_EXACT_NAMES.contains(upper)
                || upper.startsWith("AWS_")
                || upper.startsWith("GITHUB_")
                || upper.startsWith("GITLAB_")
                || upper.startsWith("BITBUCKET_")
                || upper.startsWith("GIT_CONFIG_")
                || upper.contains("TOKEN")
                || upper.contains("SECRET")
                || upper.contains("PASSWORD")
                || upper.contains("PASSWD")
                || upper.contains("CREDENTIAL")
                || upper.contains("PRIVATE_KEY")
                || upper.contains("API_KEY")
                || upper.endsWith("_KEY");
    }

    private static Path createContainedDirectory(Path root, Path directory) throws IOException {
        Path current = root;
        for (Path component : root.relativize(directory.normalize())) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IOException("Managed process runtime cannot use symbolic-link directories");
            }
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Managed process runtime path is not a directory");
            }
        }
        Files.createDirectories(directory);
        Path realDirectory = directory.toRealPath();
        if (!realDirectory.startsWith(root)) {
            throw new IOException("Managed process runtime escaped the workspace");
        }
        return realDirectory;
    }
}
