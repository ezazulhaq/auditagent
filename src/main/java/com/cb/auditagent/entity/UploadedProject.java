package com.cb.auditagent.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "uploaded_projects", indexes = {
    @Index(name = "idx_uploaded_project_user", columnList = "user_id, created_at")
})
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadedProject {
    @Id
    @EqualsAndHashCode.Include
    private String uploadId;

    @NotNull
    @Column(name = "user_id", nullable = false)
    private String userId;

    @NotNull
    @Column(name = "project_name", nullable = false)
    private String projectName;

    @NotNull
    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @NotNull
    @Column(name = "report_key", nullable = false)
    private String reportKey;

    @Column(name = "file_size_bytes")
    private long fileSizeBytes;

    @Column(name = "total_findings")
    private int totalFindings;

    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}

