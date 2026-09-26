package com.cb.auditagent.service;

import com.cb.auditagent.config.MemoryConfig;
import com.cb.auditagent.config.ScannerConfig;
import com.cb.auditagent.service.ManagedProcessRunner;
import com.cb.auditagent.service.MemoryRedactor;
import com.cb.auditagent.service.ScanExecutionException;
import com.cb.auditagent.service.ScannerService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScannerServiceTest {
    private static final String VALID_REPORT = "{\"results\":[],\"errors\":[],\"paths\":{\"scanned\":[]}}";

    @TempDir
    Path workspace;

    private ManagedProcessRunner runner;
    private ScannerService scanner;

    @BeforeEach
    void setUp() {
        runner = mock(ManagedProcessRunner.class);
        ScannerConfig config = new ScannerConfig();
        scanner = new ScannerService(new ObjectMapper(), runner, config,
                new MemoryRedactor(new MemoryConfig()));
    }

    @Test
    void acceptsSemgrepExitZero() {
        whenRun().thenReturn(new ManagedProcessRunner.ProcessResult(0, VALID_REPORT, ""));

        ScannerService.ScanResult result = scanner.scan(workspace.toString());

        assertEquals(0, result.getFindings().size());
    }

    @Test
    void acceptsSemgrepExitOneAndForwardsHeartbeatProgress() {
        List<Map<String, Object>> progress = new ArrayList<>();
        whenRun().thenAnswer(invocation -> {
            Consumer<Duration> heartbeat = invocation.getArgument(4);
            heartbeat.accept(Duration.ofSeconds(75));
            return new ManagedProcessRunner.ProcessResult(1, VALID_REPORT, "finding present");
        });

        ScannerService.ScanResult result = scanner.scan(workspace.toString(), progress::add);

        assertEquals(0, result.getFindings().size());
        assertTrue(progress.stream().anyMatch(event -> "run_semgrep".equals(event.get("step"))
                && event.get("message").toString().contains("1m 15s")));
    }

    @Test
    void rejectsUnexpectedExitAndRedactsBoundedDiagnostics() {
        whenRun().thenReturn(new ManagedProcessRunner.ProcessResult(
                2, "", "token=super-secret scanner configuration failed"));

        ScanExecutionException error = assertThrows(ScanExecutionException.class,
                () -> scanner.scan(workspace.toString()));

        assertTrue(error.getMessage().contains("code 2"));
        assertTrue(error.getMessage().contains("[REDACTED]"));
    }

    @Test
    void rejectsEmptyAndMalformedReportsInsteadOfReturningAnEmptySuccess() {
        whenRun().thenReturn(new ManagedProcessRunner.ProcessResult(0, "  ", ""));
        assertThrows(ScanExecutionException.class, () -> scanner.scan(workspace.toString()));

        whenRun().thenReturn(new ManagedProcessRunner.ProcessResult(0, "{not-json", ""));
        assertThrows(ScanExecutionException.class, () -> scanner.scan(workspace.toString()));
    }

    private org.mockito.stubbing.OngoingStubbing<ManagedProcessRunner.ProcessResult> whenRun() {
        return when(runner.run(anyList(), any(Path.class), any(Duration.class),
                any(Duration.class), any()));
    }
}
