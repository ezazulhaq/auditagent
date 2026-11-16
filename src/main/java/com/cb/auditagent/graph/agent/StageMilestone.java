package com.cb.auditagent.graph.agent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Compact summary of a completed worker stage execution.
 * Keeps supervisor context bounded (O(N)) instead of quadratic transcripts.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StageMilestone(
        @JsonProperty("stage") AgentStage stage,
        @JsonProperty("success") boolean success,
        @JsonProperty("summary") String summary,
        @JsonProperty("touchedFiles") List<String> touchedFiles
) implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonCreator
    public StageMilestone(
            @JsonProperty("stage") AgentStage stage,
            @JsonProperty("success") boolean success,
            @JsonProperty("summary") String summary,
            @JsonProperty("touchedFiles") List<String> touchedFiles) {
        this.stage = stage;
        this.success = success;
        this.summary = summary != null ? summary : "";
        this.touchedFiles = touchedFiles != null ? Collections.unmodifiableList(new ArrayList<>(touchedFiles)) : Collections.emptyList();
    }
}
