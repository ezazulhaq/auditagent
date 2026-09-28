package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "run_changes", indexes = {
    @Index(name = "idx_run_change_run", columnList = "run_id")
})
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RunChange {
    @Id
    @EqualsAndHashCode.Include
    private String changeId;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String runId;

    @jakarta.validation.constraints.NotNull
    @Column(length = 1024, nullable = false)
    private String filePath;

    private String beforeHash;

    private String afterHash;

    @Column(length = 1024)
    private String backupPath;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private String state;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime createdAt;

}
