package com.cb.auditagent.repository;

import com.cb.auditagent.entity.JobEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobEventLogRepository extends JpaRepository<JobEventLog, Long> {
    List<JobEventLog> findByJobIdOrderByEventIdAsc(String jobId);
    List<JobEventLog> findByRunIdOrderByEventIdAsc(String runId);
}
