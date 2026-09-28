package com.cb.auditagent.service;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Small subprocess used by ManagedProcessRunnerTest. It deliberately avoids
 * test-framework dependencies so it can be launched in a separate JVM.
 */
public final class ManagedProcessFixture {
    private ManagedProcessFixture() {
    }

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "flood-stderr" -> {
                System.err.print("x".repeat(1024 * 1024));
                System.err.flush();
                System.out.print(args[1]);
                System.out.flush();
            }
            case "parent-with-child" -> {
                Process child = startSleepingChild();
                Files.writeString(Path.of(args[1]), Long.toString(child.pid()));
                Thread.sleep(60_000);
            }
            case "parent-exits-with-child" -> {
                Process child = startSleepingChild();
                Files.writeString(Path.of(args[1]), Long.toString(child.pid()));
                Thread.sleep(750);
                System.exit(2);
            }
            case "print-temp" -> System.out.print(String.join("|",
                    System.getenv("TEMP"), System.getenv("TMP"), System.getenv("TMPDIR")));
            case "sleep" -> Thread.sleep(60_000);
            default -> throw new IllegalArgumentException("Unknown fixture mode");
        }
    }

    private static Process startSleepingChild() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin",
                isWindows() ? "java.exe" : "java").toString();
        Path classes = Path.of(ManagedProcessFixture.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        return new ProcessBuilder(java, "-cp", classes.toString(),
                ManagedProcessFixture.class.getName(), "sleep")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
