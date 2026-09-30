package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;

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

    private java.time.LocalDateTime createdAt;

}
