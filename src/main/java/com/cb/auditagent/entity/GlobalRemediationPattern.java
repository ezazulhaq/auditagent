package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "global_remediation_patterns",
    uniqueConstraints = { @UniqueConstraint(name = "uq_global_pattern_rule", columnNames = {"rule_id"}) })
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalRemediationPattern {
    @Id
    @EqualsAndHashCode.Include
    private String patternId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String ruleId;

    private String category;

    @Column(columnDefinition = "TEXT")
    private String positivePattern;

    @Column(columnDefinition = "TEXT")
    private String negativePattern;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Double confidence;

    private String sourceRunId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime updatedAt;

}
