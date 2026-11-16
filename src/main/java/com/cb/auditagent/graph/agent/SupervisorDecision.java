package com.cb.auditagent.graph.agent;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured decision output from the supervisor router.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SupervisorDecision(
        @JsonProperty("nextAgent")
        @JsonAlias({"next_agent", "agent", "nextStage", "next_stage", "stage"})
        String nextAgent,

        @JsonProperty("reasoning")
        @JsonAlias({"reason", "rationale", "thought", "explanation"})
        String reasoning
) {
    @JsonCreator
    public SupervisorDecision(
            @JsonProperty("nextAgent") String nextAgent,
            @JsonProperty("reasoning") String reasoning) {
        this.nextAgent = nextAgent != null ? nextAgent.trim() : "FAIL";
        this.reasoning = reasoning != null ? reasoning.trim() : "";
    }
}
