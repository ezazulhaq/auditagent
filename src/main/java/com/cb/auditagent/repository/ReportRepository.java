package com.cb.auditagent.repository;

import com.cb.auditagent.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, String> {
}
