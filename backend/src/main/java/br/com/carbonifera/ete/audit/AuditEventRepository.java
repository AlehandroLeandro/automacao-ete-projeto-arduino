package br.com.carbonifera.ete.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findAllByRecordTypeAndRecordIdOrderByOccurredAtDesc(AuditRecordType recordType, UUID recordId);
}
