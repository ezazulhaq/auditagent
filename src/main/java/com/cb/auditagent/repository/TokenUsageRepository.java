package com.cb.auditagent.repository;

import com.cb.auditagent.entity.TokenUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface TokenUsageRepository extends JpaRepository<TokenUsage, String> {
    @Query("SELECT COALESCE(t.repoName, t.repoPath) AS repoName, t.modelName AS modelName, SUM(t.tokens) AS totalTokens, COUNT(t) AS callCount FROM TokenUsage t WHERE t.userId = :userId GROUP BY COALESCE(t.repoName, t.repoPath), t.modelName ORDER BY SUM(t.tokens) DESC")
    List<TokenUsageSummary> findTokenUsageSummaryByUserId(@Param("userId") String userId);

    interface TokenUsageSummary {
        String getRepoName();
        default String getRepoPath() {
            return getRepoName();
        }
        String getModelName();
        Long getTotalTokens();
        Long getCallCount();
    }
}
