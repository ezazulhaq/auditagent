package com.cb.auditagent.graph;

import com.cb.auditagent.service.DatabaseService;
import org.springframework.stereotype.Component;

/**
 * @deprecated Deprecated in favor of {@link DatabaseCheckpointSaver}.
 * Retained for backwards compatibility with legacy callers.
 */
@Deprecated
@Component
public class DuckDbCheckpointSaver extends DatabaseCheckpointSaver {

    public DuckDbCheckpointSaver(DatabaseService db) {
        super(db);
    }
}
