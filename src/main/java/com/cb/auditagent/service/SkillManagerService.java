package com.cb.auditagent.service;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import com.cb.auditagent.domain.Skill;

import jakarta.annotation.PostConstruct;

@Service
public class SkillManagerService {
    private static final Logger logger = LoggerFactory.getLogger(SkillManagerService.class);
    public static final String DEFAULT_SKILLS_PATTERN = "classpath*:skills/**/SKILL.md";
    public static final String DEFAULT_FALLBACK_DIR = ".auditagent/skills";

    private final ResourcePatternResolver resourceResolver;
    private final String skillsPattern;
    private final String fallbackDir;
    private final Map<String, Skill> skills = new ConcurrentHashMap<>();
    private final Pattern namePattern = Pattern.compile("^[a-z0-9-]+$");

    public SkillManagerService(
            @Autowired(required = false) ResourcePatternResolver resourceResolver,
            @Value("${auditagent.skills.pattern:}") String configuredPattern,
            @Value("${auditagent.skills.location:}") String configuredLocation,
            @Value("${auditagent.skills.fallback-dir:" + DEFAULT_FALLBACK_DIR + "}") String fallbackDir) {
        this.resourceResolver = resourceResolver != null ? resourceResolver : new PathMatchingResourcePatternResolver();
        this.fallbackDir = fallbackDir;

        if (configuredPattern != null && !configuredPattern.isBlank()) {
            this.skillsPattern = configuredPattern.trim();
        } else if (configuredLocation != null && !configuredLocation.isBlank()) {
            String loc = configuredLocation.trim();
            if (loc.endsWith("/")) {
                this.skillsPattern = loc + "**/SKILL.md";
            } else {
                this.skillsPattern = loc + "/**/SKILL.md";
            }
        } else {
            this.skillsPattern = DEFAULT_SKILLS_PATTERN;
        }
    }

    public SkillManagerService() {
        this(new PathMatchingResourcePatternResolver(), "", "", DEFAULT_FALLBACK_DIR);
    }

    @PostConstruct
    public void init() {
        discoverSkills();
    }

    public synchronized void discoverSkills() {
        skills.clear();
        try {
            Resource[] resources = resourceResolver.getResources(skillsPattern);
            if (resources != null && resources.length > 0) {
                for (Resource resource : resources) {
                    if (resource != null && resource.exists()) {
                        loadSkillFromResource(resource);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error discovering skills from pattern '{}'", skillsPattern, e);
        }

        // Fallback to filesystem directory if no skills were discovered via resource resolver
        if (skills.isEmpty() && fallbackDir != null && !fallbackDir.isBlank()) {
            discoverSkillsFromDirectory(new File(fallbackDir));
        }

        if (skills.isEmpty()) {
            logger.warn("No skills discovered from pattern '{}' or fallback directory '{}'.",
                    skillsPattern, fallbackDir);
        } else {
            logger.info("Successfully discovered {} skills.", skills.size());
        }
    }

    private void loadSkillFromResource(Resource resource) {
        try (InputStream is = resource.getInputStream()) {
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            String folderName = extractFolderName(resource);
            String filePath = resolveSkillPath(resource);
            parseAndRegisterSkill(content, folderName, filePath, resource.getDescription());
        } catch (Exception e) {
            logger.error("Error loading skill from resource: " + resource.getDescription(), e);
        }
    }

    private String extractFolderName(Resource resource) {
        String path = null;
        try {
            path = resource.getURI().toString().replace('\\', '/');
        } catch (Exception e) {
            try {
                path = resource.getURL().getPath().replace('\\', '/');
            } catch (Exception ex) {
                path = resource.getDescription().replace('\\', '/');
            }
        }
        if (path == null) {
            return "";
        }
        if (path.endsWith("]")) {
            path = path.substring(0, path.length() - 1);
        }
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash > 0) {
            int prevSlash = path.lastIndexOf('/', lastSlash - 1);
            if (prevSlash >= 0) {
                return path.substring(prevSlash + 1, lastSlash);
            }
        }
        return "";
    }

    private String resolveSkillPath(Resource resource) {
        try {
            return resource.getFile().getAbsolutePath();
        } catch (Exception e) {
            try {
                return resource.getURI().toString();
            } catch (Exception ex) {
                return resource.getDescription();
            }
        }
    }

    private void discoverSkillsFromDirectory(File dir) {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] subDirs = dir.listFiles();
        if (subDirs == null) {
            return;
        }
        for (File subDir : subDirs) {
            if (subDir.isDirectory()) {
                File skillFile = new File(subDir, "SKILL.md");
                if (skillFile.exists() && skillFile.isFile()) {
                    try {
                        String content = Files.readString(skillFile.toPath(), StandardCharsets.UTF_8);
                        parseAndRegisterSkill(content, subDir.getName(), skillFile.getAbsolutePath(),
                                skillFile.getAbsolutePath());
                    } catch (Exception e) {
                        logger.error("Error loading skill from file: " + skillFile.getAbsolutePath(), e);
                    }
                }
            }
        }
    }

    private void parseAndRegisterSkill(String content, String folderName, String filePath, String sourceDesc) {
        String[] parts = content.split("---");
        if (parts.length < 2) {
            logger.warn("Skill resource {} has invalid structure.", sourceDesc);
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
                if (line.startsWith("name:") && parsedName.isEmpty()) {
                    parsedName = line.substring("name:".length()).trim();
                }
                if (line.startsWith("description:") && description.isEmpty()) {
                    description = line.substring("description:".length()).trim();
                }
            }
        }

        String finalName = parsedName.isEmpty() ? folderName : parsedName;

        if (finalName == null || finalName.isEmpty() || !namePattern.matcher(finalName).matches()) {
            throw new IllegalArgumentException(
                    "Invalid skill name '" + finalName + "' at " + sourceDesc +
                            ". Skill names may only contain lowercase letters, numbers, and hyphens.");
        }

        if (description.isEmpty()) {
            description = "Skill playbook for " + finalName;
        }

        Skill skill = new Skill(finalName, description, instructions, filePath);
        skills.put(finalName, skill);
        logger.info("Loaded skill: '{}' from {}", finalName, sourceDesc);
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

    public String getSkillsPattern() {
        return skillsPattern;
    }

    public String getFallbackDir() {
        return fallbackDir;
    }
}
