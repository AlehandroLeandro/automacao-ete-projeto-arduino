package br.com.carbonifera.ete.audit;

import br.com.carbonifera.ete.common.error.BusinessException;
import br.com.carbonifera.ete.measurement.FlowMeasurementRepository;
import br.com.carbonifera.ete.measurement.PhMeasurementRepository;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.station.StationScopeService;
import br.com.carbonifera.ete.stoppage.StoppageRepository;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AuditServiceTest {

    @Test
    void shortDescriptionFailsWithoutSavingAuditEvent() {
        AuditEventRepository auditEventRepository = mock(AuditEventRepository.class);
        AuditService service = new AuditService(
                auditEventRepository,
                mock(PhMeasurementRepository.class),
                mock(FlowMeasurementRepository.class),
                mock(StoppageRepository.class),
                mock(StationScopeService.class)
        );

        assertThatThrownBy(() -> service.recordDelete(
                AuditRecordType.PH_MEASUREMENT,
                UUID.randomUUID(),
                new Station("ITE A", "ite-a"),
                new AppUser("Operator", "operator", "hash", UserRole.OPERATOR, false),
                "curta"
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Audit description must have between 10 and 500 characters");

        verify(auditEventRepository, never()).save(any());
    }
}
