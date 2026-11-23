package br.com.ete.audit;

import br.com.ete.station.Station;
import br.com.ete.user.AppUser;
import br.com.ete.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AuditEventRepositoryTest {

    @Autowired AuditEventRepository repository;
    @Autowired org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager entityManager;

    @Test
    void existsOnlyForMatchingRecordTypeIdAndAction() {
        Station station = entityManager.persistFlushFind(new Station("Audit station", "audit-device-key"));
        AppUser admin = entityManager.persistFlushFind(new AppUser(
                "Audit admin", "audit-device-admin", "hash", UserRole.ADMIN, false));
        UUID keyId = UUID.randomUUID();
        entityManager.persistAndFlush(new AuditEvent(
                AuditRecordType.DEVICE_KEY, AuditAction.UPDATED, keyId, station, admin, "Device key updated"));

        assertThat(repository.existsByRecordTypeAndRecordIdAndAction(
                AuditRecordType.DEVICE_KEY, keyId, AuditAction.UPDATED)).isTrue();
        assertThat(repository.existsByRecordTypeAndRecordIdAndAction(
                AuditRecordType.FLOW_MEASUREMENT, keyId, AuditAction.UPDATED)).isFalse();
        assertThat(repository.existsByRecordTypeAndRecordIdAndAction(
                AuditRecordType.DEVICE_KEY, UUID.randomUUID(), AuditAction.UPDATED)).isFalse();
        assertThat(repository.existsByRecordTypeAndRecordIdAndAction(
                AuditRecordType.DEVICE_KEY, keyId, AuditAction.DELETED)).isFalse();
    }
}
