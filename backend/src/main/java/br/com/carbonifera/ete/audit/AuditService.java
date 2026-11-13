package br.com.carbonifera.ete.audit;

import br.com.carbonifera.ete.common.error.BusinessException;
import br.com.carbonifera.ete.common.error.ResourceNotFoundException;
import br.com.carbonifera.ete.measurement.FlowMeasurement;
import br.com.carbonifera.ete.measurement.FlowMeasurementRepository;
import br.com.carbonifera.ete.measurement.PhMeasurement;
import br.com.carbonifera.ete.measurement.PhMeasurementRepository;
import br.com.carbonifera.ete.security.UserPrincipal;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.station.StationScopeService;
import br.com.carbonifera.ete.stoppage.Stoppage;
import br.com.carbonifera.ete.stoppage.StoppageRepository;
import br.com.carbonifera.ete.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AuditService {

    private static final int MIN_DESCRIPTION_LENGTH = 10;
    private static final int MAX_DESCRIPTION_LENGTH = 500;

    private final AuditEventRepository auditEventRepository;
    private final PhMeasurementRepository phMeasurementRepository;
    private final FlowMeasurementRepository flowMeasurementRepository;
    private final StoppageRepository stoppageRepository;
    private final StationScopeService stationScopeService;

    public AuditService(
            AuditEventRepository auditEventRepository,
            PhMeasurementRepository phMeasurementRepository,
            FlowMeasurementRepository flowMeasurementRepository,
            StoppageRepository stoppageRepository,
            StationScopeService stationScopeService
    ) {
        this.auditEventRepository = auditEventRepository;
        this.phMeasurementRepository = phMeasurementRepository;
        this.flowMeasurementRepository = flowMeasurementRepository;
        this.stoppageRepository = stoppageRepository;
        this.stationScopeService = stationScopeService;
    }

    @Transactional
    public AuditEvent recordUpdate(
            AuditRecordType recordType,
            UUID recordId,
            Station station,
            AppUser user,
            String description,
            List<AuditFieldChangeDraft> changes
    ) {
        if (changes.isEmpty()) {
            throw new BusinessException("Update must change at least one field");
        }
        return save(recordType, AuditAction.UPDATED, recordId, station, user, description, changes);
    }

    @Transactional
    public AuditEvent recordDelete(
            AuditRecordType recordType,
            UUID recordId,
            Station station,
            AppUser user,
            String description
    ) {
        return save(recordType, AuditAction.DELETED, recordId, station, user, description, List.of());
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> history(AuditRecordType recordType, UUID recordId, UserPrincipal principal) {
        Station station = resolveRecordStation(recordType, recordId);
        stationScopeService.requireReadableStation(station, principal);
        return auditEventRepository.findAllByRecordTypeAndRecordIdOrderByOccurredAtDesc(recordType, recordId)
                .stream()
                .map(AuditEventResponse::from)
                .toList();
    }

    private AuditEvent save(
            AuditRecordType recordType,
            AuditAction action,
            UUID recordId,
            Station station,
            AppUser user,
            String description,
            List<AuditFieldChangeDraft> changes
    ) {
        String normalizedDescription = normalizeDescription(description);
        AuditEvent event = new AuditEvent(recordType, action, recordId, station, user, normalizedDescription);
        changes.stream()
                .map(AuditFieldChangeDraft::toEntity)
                .forEach(event::addChange);
        return auditEventRepository.save(event);
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            throw new BusinessException("Audit description is required");
        }
        String trimmed = description.trim();
        if (trimmed.length() < MIN_DESCRIPTION_LENGTH || trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException("Audit description must have between 10 and 500 characters");
        }
        return trimmed;
    }

    private Station resolveRecordStation(AuditRecordType recordType, UUID recordId) {
        return switch (recordType) {
            case PH_MEASUREMENT -> {
                PhMeasurement measurement = phMeasurementRepository.findById(recordId)
                        .orElseThrow(() -> new ResourceNotFoundException("pH measurement not found"));
                yield measurement.getStation();
            }
            case FLOW_MEASUREMENT -> {
                FlowMeasurement measurement = flowMeasurementRepository.findById(recordId)
                        .orElseThrow(() -> new ResourceNotFoundException("Flow measurement not found"));
                yield measurement.getStation();
            }
            case STOPPAGE -> {
                Stoppage stoppage = stoppageRepository.findById(recordId)
                        .orElseThrow(() -> new ResourceNotFoundException("Stoppage not found"));
                yield stoppage.getStation();
            }
        };
    }
}
