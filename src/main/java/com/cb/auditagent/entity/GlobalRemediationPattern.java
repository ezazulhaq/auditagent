package com.cb.auditagent.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @NotNull
    @Column(nullable = false)
    private String ruleId;

    private String category;

    @Column(columnDefinition = "TEXT")
    private String positivePattern;

    @Column(columnDefinition = "TEXT")
    private String negativePattern;

    @NotNull
    @Column(nullable = false)
    private Double confidence;

    private String sourceRunId;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @NotNull
    @Column(nullable = false)
    private LocalDateTime updatedAt;

}
