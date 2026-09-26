package com.cb.auditagent.graph.agent;

import java.util.List;

public enum AgentStage {

        PLANNING("Remediation Planning", "stage-planning", List.of("read_file", "search_codebase")),

        DISCOVERY("Repository Discovery", "stage-discovery", List.of("read_file", "search_codebase")),

        MEMORY_REVIEW("Memory Review", "stage-memory-review", List.of("search_rejected_memories", "read_file")),

        BASELINE("Baseline Validation", "stage-baseline",
                        List.of("compile_project", "run_tests", "run_linter", "run_static_analysis")),

        SYNTAX("Syntax and Parsing Check", "stage-syntax", List.of("compile_project", "run_linter")),

        FORMATTING("Formatting and Style Check", "stage-formatting", List.of("run_linter", "apply_patch", "read_file")),

        SYMBOLS("Variable and Symbol Consistency Check", "stage-symbols",
                        List.of("run_static_analysis", "search_codebase", "read_file")),

        TYPING("Type and Interface Validation", "stage-typing",
                        List.of("run_static_analysis", "compile_project", "read_file")),

        CONTROL_FLOW("Control-Flow and Logic Review", "stage-control-flow", List.of("read_file", "search_codebase")),

        DATA_FLOW("Data-Flow and State Analysis", "stage-data-flow",
                        List.of("read_file", "search_codebase", "run_static_analysis")),

        ERROR_HANDLING("Error-Handling Review", "stage-error-handling", List.of("read_file", "search_codebase")),

        SECURITY("Security Review", "stage-security",
                        List.of("rescan_file", "read_file", "search_codebase", "run_static_analysis")),

        CONCURRENCY("Concurrency and Distributed-System Review", "stage-concurrency",
                        List.of("read_file", "search_codebase")),

        PERFORMANCE("Performance and Resource Review", "stage-performance", List.of("read_file", "search_codebase")),

        ARCHITECTURE("Maintainability and Architecture Review", "stage-architecture",
                        List.of("read_file", "search_codebase")),

        ROOT_CAUSE("Root-Cause Analysis and Minimal Fix", "stage-root-cause",
                        List.of("read_file", "apply_patch", "rollback_file", "search_codebase")),

        POST_FIX("Post-Fix Validation and Diff Review", "stage-post-fix",
                        List.of("compile_project", "run_tests", "run_linter", "run_static_analysis", "rescan_file"));

        private final String stageName;
        private final String skillName;
        private final List<String> allowedTools;

        AgentStage(String stageName, String skillName, List<String> allowedTools) {
                this.stageName = stageName;
                this.skillName = skillName;
                this.allowedTools = allowedTools;
        }

        public String getStageName() {
                return stageName;
        }

        public String getSkillName() {
                return skillName;
        }

        public List<String> getAllowedTools() {
                return allowedTools;
        }
}
