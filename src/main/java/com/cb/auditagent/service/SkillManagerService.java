package com.cb.auditagent.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.cb.auditagent.domain.Skill;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class SkillManagerService {
    private static final Logger logger = LoggerFactory.getLogger(SkillManagerService.class);
    private final String skillsDir = ".auditagent/skills";
    private final Map<String, Skill> skills = new HashMap<>();
    private final Pattern namePattern = Pattern.compile("^[a-z0-9-]+$");

    @PostConstruct
    public void init() {
        discoverSkills();
    }

    public synchronized void discoverSkills() {
        skills.clear();
        File dir = new File(skillsDir);
        if (!dir.exists() || !dir.isDirectory()) {
            logger.warn("Skills directory {} does not exist. Please check your workspace setup.",
                    dir.getAbsolutePath());
            return;
        }

        File[] subDirs = dir.listFiles();
        if (subDirs == null)
            return;

        for (File subDir : subDirs) {
            if (subDir.isDirectory()) {
                File skillFile = new File(subDir, "SKILL.md");
                if (skillFile.exists() && skillFile.isFile()) {
                    loadSkill(subDir.getName(), skillFile);
                }
            }
        }
    }

    private void loadSkill(String folderName, File file) {
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            String[] parts = content.split("---");
            if (parts.length < 2) {
                logger.warn("Skill file {} has invalid structure.", file.getName());
                return;
            }

            String instructions = parts[parts.length - 1].trim();
            String description = "";
            String parsedName = "";

            // Search for name and description inside frontmatter block
            for (int i = 1; i < parts.length - 1; i++) {
                String block = parts[i];
                for (String line : block.split("\n")) {
                    line = line.trim();
                    if (line.startsWith("name:")) {
                        parsedName = line.substring("name:".length()).trim();
                    }
                    if (line.startsWith("description:")) {
                        description = line.substring("description:".length()).trim();
                    }
                }
            }

            String finalName = parsedName.isEmpty() ? folderName : parsedName;

            if (!namePattern.matcher(finalName).matches()) {
                throw new IllegalArgumentException(
                        "Invalid skill name '" + finalName + "' at " + file.getAbsolutePath() +
                                ". Skill names may only contain lowercase letters, numbers, and hyphens.");
            }

            if (description.isEmpty()) {
                description = "Skill playbook for " + finalName;
            }

            Skill skill = new Skill(finalName, description, instructions, file.getAbsolutePath());
            skills.put(finalName, skill);
            logger.info("Loaded skill: '{}' from {}", finalName, file.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Error loading skill from " + file.getAbsolutePath(), e);
        }
    }

    public Skill getSkill(String name) {
        // Rediscover to allow dynamic updates
        discoverSkills();
        return skills.get(name);
    }

    public Map<String, Skill> getAllSkills() {
        discoverSkills();
        return new HashMap<>(skills);
    }
}
