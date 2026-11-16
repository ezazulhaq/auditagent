package com.cb.auditagent.graph.agent;

import com.cb.auditagent.graph.state.MultiAgentState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StageMilestoneSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("StageMilestone must be Serializable with Java standard ObjectOutputStream")
    void testStageMilestoneJavaSerialization() throws Exception {
        StageMilestone original = new StageMilestone(
                AgentStage.DISCOVERY,
                true,
                "Discovery completed successfully with AST extraction",
                List.of("src/main/java/App.java", "pom.xml")
        );

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }

        StageMilestone deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            deserialized = (StageMilestone) ois.readObject();
        }

        assertNotNull(deserialized);
        assertEquals(original.stage(), deserialized.stage());
        assertEquals(original.success(), deserialized.success());
        assertEquals(original.summary(), deserialized.summary());
        assertEquals(original.touchedFiles(), deserialized.touchedFiles());
    }

    @Test
    @DisplayName("SupervisorDecision must be Serializable with Java standard ObjectOutputStream")
    void testSupervisorDecisionJavaSerialization() throws Exception {
        SupervisorDecision original = new SupervisorDecision("SECURITY_REVIEW", "Reviewing modified files");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }

        SupervisorDecision deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            deserialized = (SupervisorDecision) ois.readObject();
        }

        assertNotNull(deserialized);
        assertEquals("SECURITY_REVIEW", deserialized.nextAgent());
        assertEquals("Reviewing modified files", deserialized.reasoning());
    }

    @Test
    @DisplayName("LangGraph4j ObjectStreamStateSerializer must successfully serialize map with currentMilestone")
    void testLangGraph4jSerializerWithMilestone() throws Exception {
        ObjectStreamStateSerializer<AgentState> serializer = new ObjectStreamStateSerializer<>(AgentState::new);

        StageMilestone milestone = new StageMilestone(
                AgentStage.SYNTAX,
                true,
                "Syntax verification passed",
                List.of("src/App.java")
        );

        Map<String, Object> data = Map.of(
                "currentMilestone", milestone,
                "milestones", new ArrayList<>(List.of(milestone)),
                "stage", AgentStage.SYNTAX.name()
        );

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            serializer.writeData(data, oos);
        }

        Map<String, Object> readData;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            readData = serializer.readData(ois);
        }

        assertNotNull(readData);
        assertTrue(readData.containsKey("currentMilestone"));
        Object deserializedMilestone = readData.get("currentMilestone");
        assertInstanceOf(StageMilestone.class, deserializedMilestone);
        StageMilestone sm = (StageMilestone) deserializedMilestone;
        assertEquals(AgentStage.SYNTAX, sm.stage());
        assertTrue(sm.success());
    }

    @Test
    @DisplayName("StageMilestone JSON serialization and deserialization via Jackson")
    void testStageMilestoneJacksonSerialization() throws Exception {
        StageMilestone milestone = new StageMilestone(
                AgentStage.ROOT_CAUSE,
                false,
                "Root cause hypothesis unconfirmed",
                List.of("src/Database.java")
        );

        String json = objectMapper.writeValueAsString(milestone);
        StageMilestone fromJson = objectMapper.readValue(json, StageMilestone.class);

        assertEquals(milestone.stage(), fromJson.stage());
        assertEquals(milestone.success(), fromJson.success());
        assertEquals(milestone.summary(), fromJson.summary());
        assertEquals(milestone.touchedFiles(), fromJson.touchedFiles());
    }

    @Test
    @DisplayName("MultiAgentState correctly handles both StageMilestone instances and Map representations")
    void testMultiAgentStateHandlesBothRepresentations() {
        StageMilestone milestone1 = new StageMilestone(AgentStage.PLANNING, true, "Plan ready", List.of());
        Map<String, Object> milestoneMap2 = Map.of(
                "stage", "DISCOVERY",
                "success", true,
                "summary", "Discovery complete",
                "touchedFiles", List.of("FileA.java")
        );

        MultiAgentState state = new MultiAgentState(Map.of(
                "milestones", List.of(milestone1, milestoneMap2)
        ));

        List<StageMilestone> milestones = state.getMilestones();
        assertEquals(2, milestones.size());
        assertEquals(AgentStage.PLANNING, milestones.get(0).stage());
        assertEquals(AgentStage.DISCOVERY, milestones.get(1).stage());
        assertEquals(List.of("FileA.java"), milestones.get(1).touchedFiles());
    }
}
