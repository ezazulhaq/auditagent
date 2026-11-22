package com.cb.auditagent.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.cb.auditagent.domain.Skill;

class SkillManagerServiceTest {

    @Test
    void testDiscoverSkillsFromClasspath() {
        SkillManagerService service = new SkillManagerService();
        service.init();

        Map<String, Skill> allSkills = service.getAllSkills();
        assertNotNull(allSkills);
        assertTrue(allSkills.size() >= 20, "Should discover at least 20 skills from classpath, found: " + allSkills.size());

        // Verify key runtime remediation skills
        Skill planning = service.getSkill("stage-planning");
        assertNotNull(planning, "stage-planning skill should exist");
        assertEquals("stage-planning", planning.getName());
        assertNotNull(planning.getDescription());
        assertFalse(planning.getDescription().isBlank());
        assertNotNull(planning.getInstructions());
        assertTrue(planning.getInstructions().contains("Remediation Plan") || !planning.getInstructions().isBlank());

        Skill patchEngineer = service.getSkill("patch-engineer");
        assertNotNull(patchEngineer, "patch-engineer skill should exist");
        assertEquals("patch-engineer", patchEngineer.getName());
        assertNotNull(patchEngineer.getInstructions());

        Skill architecture = service.getSkill("stage-architecture");
        assertNotNull(architecture, "stage-architecture skill should exist");
        assertEquals("stage-architecture", architecture.getName());

        Skill rootCause = service.getSkill("stage-root-cause");
        assertNotNull(rootCause, "stage-root-cause skill should exist");
        assertEquals("stage-root-cause", rootCause.getName());

        Skill dbManager = service.getSkill("db-manager");
        assertNotNull(dbManager, "db-manager skill should exist");
        assertEquals("db-manager", dbManager.getName());
    }

    @Test
    void testDiscoverSkillsWithTargetedPattern() {
        String pattern = "classpath*:skills/stage-planning/SKILL.md";
        SkillManagerService service = new SkillManagerService(
                new PathMatchingResourcePatternResolver(),
                pattern,
                "",
                ""
        );
        service.init();

        Map<String, Skill> allSkills = service.getAllSkills();
        assertEquals(1, allSkills.size());
        assertNotNull(service.getSkill("stage-planning"));
        assertNull(service.getSkill("stage-architecture"));
    }

    @Test
    void testFallbackToDirectoryWhenClasspathEmpty(@TempDir Path tempDir) throws IOException {
        Path customSkillDir = tempDir.resolve("custom-skill");
        Files.createDirectories(customSkillDir);
        Path skillFile = customSkillDir.resolve("SKILL.md");
        String content = """
                ---
                name: custom-skill
                description: Custom fallback test skill
                ---
                Follow these fallback instructions carefully.
                """;
        Files.writeString(skillFile, content);

        // Pattern points to non-existent resources; fallback points to tempDir
        SkillManagerService service = new SkillManagerService(
                new PathMatchingResourcePatternResolver(),
                "classpath*:nonexistent-pattern-xyz/**/SKILL.md",
                "",
                tempDir.toAbsolutePath().toString()
        );
        service.init();

        Skill customSkill = service.getSkill("custom-skill");
        assertNotNull(customSkill, "Should load skill from fallback filesystem directory");
        assertEquals("custom-skill", customSkill.getName());
        assertEquals("Custom fallback test skill", customSkill.getDescription());
        assertTrue(customSkill.getInstructions().contains("Follow these fallback instructions"));
    }

    @Test
    void testConfiguredLocationDerivesPattern() {
        SkillManagerService service = new SkillManagerService(
                new PathMatchingResourcePatternResolver(),
                "",
                "classpath:skills/",
                ""
        );
        assertEquals("classpath:skills/**/SKILL.md", service.getSkillsPattern());
    }
}
