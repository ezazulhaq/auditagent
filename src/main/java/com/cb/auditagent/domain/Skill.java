package com.cb.auditagent.domain;

public class Skill {
    private String name;
    private String description;
    private String instructions;
    private String filePath;

    public Skill() {
    }

    public Skill(String name, String description, String instructions, String filePath) {
        this.name = name;
        this.description = description;
        this.instructions = instructions;
        this.filePath = filePath;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
}
