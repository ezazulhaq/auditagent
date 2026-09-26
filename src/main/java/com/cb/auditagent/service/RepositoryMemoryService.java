package com.cb.auditagent.service;

import org.springframework.stereotype.Service;

import com.cb.auditagent.config.MemoryConfig;
import com.cb.auditagent.domain.RepositoryMemory;
import com.cb.auditagent.domain.Vulnerability;

import java.util.List;

@Service
public class RepositoryMemoryService {
    private final DatabaseService databaseService;
    private final MemoryConfig memoryConfig;

    public RepositoryMemoryService(DatabaseService databaseService, MemoryConfig memoryConfig) {
        this.databaseService = databaseService;
        this.memoryConfig = memoryConfig;
    }

    public String retrieveForPrompt(String repoPath, Vulnerability vulnerability) {
        if (!memoryConfig.isEnabled())
            return "No approved remediation memories matched this task.";
        StringBuilder result = new StringBuilder();

        // 1. Global Pattern (cross-repository intelligence)
        databaseService.getGlobalPattern(vulnerability.getRuleId()).ifPresent(global -> {
            result.append("### Global Security Intelligence for ").append(vulnerability.getRuleId()).append("\n\n");
            if (global.negativePattern() != null && !global.negativePattern().isBlank()) {
                result.append("**ANTI-PATTERNS (What NOT to do):**\n").append(global.negativePattern()).append("\n\n");
            }
            if (global.positivePattern() != null && !global.positivePattern().isBlank()) {
                result.append("**PROVEN FIXES:**\n").append(global.positivePattern()).append("\n\n");
            }
        });

        // 2. Repository-specific memories
        List<RepositoryMemory> memories = databaseService.searchRepositoryMemories(repoPath, vulnerability);
        if (memories.isEmpty() && result.isEmpty())
            return "No approved remediation memories matched this task.";

        if (!memories.isEmpty()) {
            result.append("### Previously approved repository remediations:\n");
            for (RepositoryMemory memory : memories) {
                result.append("- ").append(memory.title()).append(" [")
                        .append(memory.category()).append("] in ").append(memory.filePattern()).append(": ")
                        .append(memory.summary()).append("\n");
            }
        }
        return result.toString();
    }
}
