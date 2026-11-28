package com.cb.auditagent.graph;

import com.cb.auditagent.service.DatabaseService;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DatabaseCheckpointSaverTest {

    private DatabaseService databaseService;
    private DatabaseCheckpointSaver checkpointSaver;

    @BeforeEach
    void setUp() {
        databaseService = mock(DatabaseService.class);
        checkpointSaver = new DatabaseCheckpointSaver(databaseService);
    }

    @Test
    void listReturnsParsedCheckpoints() {
        RunnableConfig config = RunnableConfig.builder().threadId("thread-1").build();
        when(databaseService.listGraphCheckpoints("thread-1")).thenReturn(List.of("cp-1", "cp-2"));
        when(databaseService.loadGraphCheckpoint("cp-1")).thenReturn(new String[]{"{\"key\":\"v1\"}", "nodeA"});
        when(databaseService.loadGraphCheckpoint("cp-2")).thenReturn(new String[]{"{\"key\":\"v2\"}", "nodeB:nodeC"});

        Collection<Checkpoint> list = checkpointSaver.list(config);

        assertEquals(2, list.size());
        List<Checkpoint> checkList = new ArrayList<>(list);
        assertEquals("cp-1", checkList.get(0).getId());
        assertEquals("nodeA", checkList.get(0).getNodeId());
        assertEquals("cp-2", checkList.get(1).getId());
        assertEquals("nodeB", checkList.get(1).getNodeId());
        assertEquals("nodeC", checkList.get(1).getNextNodeId());
    }

    @Test
    void getReturnsLatestCheckpointWhenNoIdProvided() {
        RunnableConfig config = RunnableConfig.builder().threadId("thread-1").build();
        when(databaseService.listGraphCheckpoints("thread-1")).thenReturn(List.of("cp-1", "cp-2"));
        when(databaseService.loadGraphCheckpoint("cp-2")).thenReturn(new String[]{"{\"key\":\"v2\"}", "nodeB"});

        Optional<Checkpoint> cp = checkpointSaver.get(config);

        assertTrue(cp.isPresent());
        assertEquals("cp-2", cp.get().getId());
        assertEquals("v2", cp.get().getState().get("key"));
    }

    @Test
    void putSavesCheckpointAndReturnsUpdatedConfig() throws Exception {
        RunnableConfig config = RunnableConfig.builder().threadId("thread-1").build();
        Checkpoint cp = Checkpoint.builder()
                .id("cp-new")
                .nodeId("agentNode")
                .nextNodeId("supervisorNode")
                .state(Map.of("runId", "run-10", "status", "IN_PROGRESS"))
                .build();

        RunnableConfig updated = checkpointSaver.put(config, cp);

        assertNotNull(updated);
        assertEquals("thread-1", updated.threadId().orElse(null));
        assertEquals("cp-new", updated.checkPointId().orElse(null));

        verify(databaseService).saveGraphCheckpoint(
                eq("cp-new"), eq("run-10"), eq("thread-1"), isNull(),
                eq("agentNode:supervisorNode"), contains("IN_PROGRESS"), isNull()
        );
    }

    @Test
    void releaseReturnsTagWithLatestCheckpointFirst() throws Exception {
        RunnableConfig config = RunnableConfig.builder().threadId("thread-1").build();
        when(databaseService.listGraphCheckpoints("thread-1")).thenReturn(List.of("cp-1", "cp-2"));
        when(databaseService.loadGraphCheckpoint("cp-1")).thenReturn(new String[]{"{\"step\":\"1\"}", "firstNode"});
        when(databaseService.loadGraphCheckpoint("cp-2")).thenReturn(new String[]{"{\"step\":\"2\"}", "latestNode"});

        BaseCheckpointSaver.Tag tag = checkpointSaver.release(config);

        assertNotNull(tag);
        assertEquals("thread-1", tag.threadId());
        assertTrue(tag.lastCheckpoint().isPresent());
        // In BaseCheckpointSaver$Tag, lastCheckpoint() returns checkpoints().stream().findFirst().
        // Since we reverse the list in release(), the latest checkpoint (cp-2) must be first!
        assertEquals("cp-2", tag.lastCheckpoint().get().getId());
        assertEquals("latestNode", tag.lastCheckpoint().get().getNodeId());
        assertEquals("2", tag.lastCheckpoint().get().getState().get("step"));
    }

    @Test
    void releaseWithEmptyDatabaseReturnsFallbackCheckpointNeverThrows() throws Exception {
        RunnableConfig config = RunnableConfig.builder().threadId("empty-thread").build();
        when(databaseService.listGraphCheckpoints("empty-thread")).thenReturn(Collections.emptyList());

        BaseCheckpointSaver.Tag tag = checkpointSaver.release(config);

        assertNotNull(tag);
        assertTrue(tag.lastCheckpoint().isPresent(), "Tag lastCheckpoint must never be empty");
        // Verify that LangGraph4j's asLastCheckpointStateData operation will not throw
        Map<String, Object> state = tag.lastCheckpoint()
                .map(Checkpoint::getState)
                .orElseThrow(() -> new IllegalStateException("Checkpoint saver tag doesn't contain any checkpoint"));
        assertNotNull(state);
    }

    @Test
    void tagMethodDelegatesToRelease() throws Exception {
        RunnableConfig config = RunnableConfig.builder().threadId("thread-1").build();
        when(databaseService.listGraphCheckpoints("thread-1")).thenReturn(Collections.emptyList());

        Optional<BaseCheckpointSaver.Tag> optTag = checkpointSaver.tag(config, null);

        assertTrue(optTag.isPresent());
        assertTrue(optTag.get().lastCheckpoint().isPresent());
    }
}
