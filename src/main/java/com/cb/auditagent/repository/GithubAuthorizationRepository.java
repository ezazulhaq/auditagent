package com.cb.auditagent.repository;

import com.cb.auditagent.entity.GithubAuthorization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GithubAuthorizationRepository extends JpaRepository<GithubAuthorization, String> {
}
