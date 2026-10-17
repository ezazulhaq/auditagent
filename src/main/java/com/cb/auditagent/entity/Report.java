package com.cb.auditagent.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reports")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {
    @Id
    @EqualsAndHashCode.Include
    private String repoPath;

    @Column(columnDefinition = "TEXT")
    private String mdReport;

    @Column(columnDefinition = "TEXT")
    private String htmlReport;

    @Column(columnDefinition = "TEXT")
    private String findingsJson;

    @Column(columnDefinition = "TEXT")
    private String metadataJson;

    private LocalDateTime createdAt;

}
