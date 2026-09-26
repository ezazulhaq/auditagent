package com.cb.auditagent.graph;

import com.cb.auditagent.service.DatabaseService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.BaseCheckpointSaver;
import org.bsc.langgraph4j.checkpoint.Checkpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DuckDbCheckpointSaver implements BaseCheckpointSaver {

    private static final Logger logger = LoggerFactory.getLogger(DuckDbCheckpointSaver.class);
    private final DatabaseService db;
    private final ObjectMapper mapper;

    public DuckDbCheckpointSaver(DatabaseService db) {
        this.db = db;
        this.mapper = new ObjectMapper();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<Checkpoint> list(RunnableConfig config) {
        String threadId = config.threadId().orElse(null);
        if (threadId == null)
            return Collections.emptyList();

        List<String> checkpointIds = db.listGraphCheckpoints(threadId);
        List<Checkpoint> checkpoints = new ArrayList<>();

        for (String id : checkpointIds) {
            String[] checkpointData = db.loadGraphCheckpoint(id);
            if (checkpointData != null) {
                try {
                    String json = checkpointData[0];
                    String nodeId = checkpointData[1] != null ? checkpointData[1] : "unknown";
                    Map<String, Object> stateMap = mapper.readValue(json, Map.class);
                    checkpoints.add(Checkpoint.builder()
                            .id(id)
                            .nodeId(nodeId)
                            .nextNodeId(nodeId)
                            .state(stateMap)
                            // We don't necessarily have nodeId here unless we parse it from state
                            // LangGraph4j usually puts metadata in a specific way, but returning just state
                            // is enough for most logic
                            .build());
                } catch (Exception e) {
                    logger.warn("Failed to parse checkpoint {}: {}", id, e.getMessage());
                }
            }
        }
        return checkpoints;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Optional<Checkpoint> get(RunnableConfig config) {
        String id = config.checkPointId().orElse(null);
        String threadId = config.threadId().orElse(null);

        if (id == null) {
            if (threadId == null)
                return Optional.empty();
            List<String> checkpointIds = db.listGraphCheckpoints(threadId);
            if (checkpointIds.isEmpty())
                return Optional.empty();
            id = checkpointIds.get(checkpointIds.size() - 1);
        }

        String[] checkpointData = db.loadGraphCheckpoint(id);
        if (checkpointData == null)
            return Optional.empty();

        try {
            String json = checkpointData[0];
            String fullNodeName = checkpointData[1] != null ? checkpointData[1] : "unknown";
            String nodeId = fullNodeName;
            String nextNodeId = fullNodeName;
            if (fullNodeName.contains(":")) {
                String[] parts = fullNodeName.split(":", 2);
                nodeId = parts[0];
                nextNodeId = parts[1];
            }
            Map<String, Object> stateMap = mapper.readValue(json, Map.class);
            return Optional.of(Checkpoint.builder()
                    .id(id)
                    .nodeId(nodeId)
                    .nextNodeId(nextNodeId)
                    .state(stateMap)
                    .build());
        } catch (Exception e) {
            logger.warn("Failed to parse checkpoint {}: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public RunnableConfig put(RunnableConfig config, Checkpoint checkpoint) throws Exception {
        String threadId = config.threadId().orElse(THREAD_ID_DEFAULT);

        String id = checkpoint.getId();
        if (id == null) {
            id = UUID.randomUUID().toString();
        }

        String json = mapper.writeValueAsString(checkpoint.getState());

        String runId = threadId;
        if (threadId.contains("-")) {
            runId = threadId.substring(0, threadId.indexOf("-"));
        }

        String combinedNodeName = checkpoint.getNodeId();
        if (checkpoint.getNextNodeId() != null) {
            combinedNodeName += ":" + checkpoint.getNextNodeId();
        }

        db.saveGraphCheckpoint(id, runId, threadId, null, combinedNodeName, json, null);

        return RunnableConfig.builder(config)
                .threadId(threadId)
                .checkPointId(id)
                .build();
    }

    @Override
    public Tag release(RunnableConfig config) throws Exception {
        return new Tag(config.threadId().orElse("default"), java.util.Collections.emptyList());
    }

    @Override
    public Optional<Tag> tag(RunnableConfig config, Integer index) throws Exception {
        throw new UnsupportedOperationException("tag not implemented");
    }

    @Override
    public void putSubGraphSaver(RunnableConfig config, RunnableConfig subConfig, BaseCheckpointSaver saver) {
        throw new UnsupportedOperationException("putSubGraphSaver not implemented");
    }

    @Override
    public Collection<SubGraphSaver> listSubGraphSaver(RunnableConfig config) {
        return Collections.emptyList();
    }
}
