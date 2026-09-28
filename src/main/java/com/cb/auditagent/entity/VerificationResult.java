package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "verification_results",
    indexes = { @Index(name = "idx_verification_result_run", columnList = "run_id") })
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationResult {
    @Id
    @EqualsAndHashCode.Include
    private String verificationId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String runId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String kind;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String evidenceRedacted;

    private Integer exitCode;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

}
