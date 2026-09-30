package com.cb.auditagent.entity;

import jakarta.persistence.*;
import lombok.*;

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

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Long sourceVersion;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private Long indexedVersion;

    private String lastResult;

    @jakarta.validation.constraints.NotNull
    @Column(nullable = false)
    private java.time.LocalDateTime updatedAt;

}
