package com.cb.auditagent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "memory_fts_state")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemoryFtsState {
    @Id
    @EqualsAndHashCode.Include
    private Integer stateId;

    @NotNull
    @Column(nullable = false)
    private Long sourceVersion;

    @NotNull
    @Column(nullable = false)
    private Long indexedVersion;

    private String lastResult;

    @NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime updatedAt;

}
