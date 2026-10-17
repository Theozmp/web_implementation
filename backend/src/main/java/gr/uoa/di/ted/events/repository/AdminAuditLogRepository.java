package gr.uoa.di.ted.events.repository;

import gr.uoa.di.ted.events.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {}
