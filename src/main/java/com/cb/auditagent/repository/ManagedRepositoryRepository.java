package com.cb.auditagent.repository;

import com.cb.auditagent.entity.ManagedRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ManagedRepositoryRepository extends JpaRepository<ManagedRepository, Long> {
    Optional<ManagedRepository> findByFullName(String fullName);
    List<ManagedRepository> findByInstallationId(Long installationId);
}
