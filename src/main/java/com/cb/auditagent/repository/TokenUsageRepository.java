package com.cb.auditagent.repository;

import com.cb.auditagent.entity.TokenUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface TokenUsageRepository extends JpaRepository<TokenUsage, String> {
    @Query("SELECT t.repoPath AS repoPath, t.modelName AS modelName, SUM(t.tokens) AS totalTokens, COUNT(t) AS callCount FROM TokenUsage t WHERE t.userId = :userId GROUP BY t.repoPath, t.modelName ORDER BY SUM(t.tokens) DESC")
    List<TokenUsageSummary> findTokenUsageSummaryByUserId(@Param("userId") String userId);

    interface TokenUsageSummary {
        String getRepoPath();
        String getModelName();
        Long getTotalTokens();
        Long getCallCount();
    }
}
