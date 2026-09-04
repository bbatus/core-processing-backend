package com.vodafone.genaiops.cpb.repository;

import com.vodafone.genaiops.cpb.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
