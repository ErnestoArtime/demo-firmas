package com.example.demo.signing;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByDraftIdOrderByTimestampAsc(String draftId);
}
