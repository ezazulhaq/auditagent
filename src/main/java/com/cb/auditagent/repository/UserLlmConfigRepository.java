package com.cb.auditagent.repository;

import com.cb.auditagent.entity.UserLlmConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserLlmConfigRepository extends JpaRepository<UserLlmConfig, String> {
}

