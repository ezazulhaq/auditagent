package com.cb.auditagent.repository;

import com.cb.auditagent.entity.GlobalRemediationPattern;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface GlobalRemediationPatternRepository extends JpaRepository<GlobalRemediationPattern, String> {
    Optional<GlobalRemediationPattern> findByRuleId(String ruleId);
}
