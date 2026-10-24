package com.cb.auditagent.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cb.auditagent.entity.UploadedProject;

@Repository
public interface UploadedProjectRepository extends JpaRepository<UploadedProject, String> {
    Optional<UploadedProject> findByUploadIdAndUserId(String uploadId, String userId);

    List<UploadedProject> findByUserIdOrderByCreatedAtDesc(String userId);
}

