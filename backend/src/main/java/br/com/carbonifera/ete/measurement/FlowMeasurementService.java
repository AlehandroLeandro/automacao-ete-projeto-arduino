package br.com.carbonifera.ete.measurement;

import br.com.carbonifera.ete.audit.AuditDescriptionRequest;
import br.com.carbonifera.ete.audit.AuditAction;
import br.com.carbonifera.ete.audit.AuditEventResponse;
import br.com.carbonifera.ete.audit.AuditFieldChangeDraft;
import br.com.carbonifera.ete.audit.AuditRecordType;
import br.com.carbonifera.ete.audit.AuditService;
import br.com.carbonifera.ete.common.error.BusinessException;
import br.com.carbonifera.ete.common.error.ResourceNotFoundException;
import br.com.carbonifera.ete.common.pagination.PageSizeValidator;
import br.com.carbonifera.ete.security.CurrentUserService;
import br.com.carbonifera.ete.security.OperatorAuthorshipPolicy;
import br.com.carbonifera.ete.security.UserPrincipal;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.station.StationScopeService;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class FlowMeasurementService {

    private static final String FUTURE_TIME_MESSAGE = "O horário não pode ser maior que o horário atual";
    private static final String NOT_FOUND_MESSAGE = "Flow measurement not found";

    private final FlowMeasurementRepository repository;
    private final StationScopeService stationScopeService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final Clock clock;
    private final OperatorAuthorshipPolicy authorshipPolicy;

    @Autowired
    public FlowMeasurementService(
            FlowMeasurementRepository repository,
            StationScopeService stationScopeService,
            CurrentUserService currentUserService,
            AuditService auditService,
            OperatorAuthorshipPolicy authorshipPolicy
    ) {
        this(repository, stationScopeService, currentUserService, auditService, Clock.systemUTC(), authorshipPolicy);
    }

    FlowMeasurementService(
            FlowMeasurementRepository repository,
            StationScopeService stationScopeService,
            CurrentUserService currentUserService,
            AuditService auditService,
            Clock clock
    ) {
        this(repository, stationScopeService, currentUserService, auditService, clock, new OperatorAuthorshipPolicy());
    }

    FlowMeasurementService(
            FlowMeasurementRepository repository,
            StationScopeService stationScopeService,
            CurrentUserService currentUserService,
            AuditService auditService,
            Clock clock,
            OperatorAuthorshipPolicy authorshipPolicy
    ) {
        this.repository = repository;
        this.stationScopeService = stationScopeService;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.clock = clock;
        this.authorshipPolicy = authorshipPolicy;
    }

    @Transactional
    public FlowMeasurementResponse create(FlowMeasurementRequest request, UserPrincipal principal) {
        requireNotFuture(request.measuredAt());
        Station station = stationScopeService.requireWritableStation(request.stationId(), principal);
        AppUser user = currentUserService.requireUser(principal);
        if (request.clientRequestId() != null) {
            var existing = repository.findByClientRequestId(request.clientRequestId());
            if (existing.isPresent() && existing.get().getCreatedBy().getId().equals(user.getId())
                    && existing.get().getStation().getId().equals(station.getId())) return FlowMeasurementResponse.from(existing.get());
        }
        BigDecimal flowLiters = FlowRateCalculator.flowLitersFromHeightCm(request.heightCm());
        FlowMeasurement saved = new FlowMeasurement(station, flowLiters, request.measuredAt(), user);
        saved.setHeightCm(request.heightCm());
        saved.setClientRequestId(request.clientRequestId());
        saved = repository.save(saved);
        return FlowMeasurementResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<FlowMeasurementResponse> list(
            UUID stationId,
            boolean consolidated,
            Instant startAt,
            Instant endAt,
            boolean includeDeleted,
            UserPrincipal principal
    ) {
        requireValidPeriod(startAt, endAt);
        List<Station> stations = stationScopeService.resolveReadableStations(stationId, consolidated, principal);
        return repository.findForList(stations, startAt, endAt, includeDeleted, authorshipPolicy.authorFilterOrNull(principal))
                .stream()
                .map(FlowMeasurementResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<FlowMeasurementResponse> list(UUID stationId, boolean consolidated, Instant startAt, Instant endAt,
                                               boolean includeDeleted, UUID authorUserId, int page, int size,
                                               UserPrincipal principal) {
        PageSizeValidator.validate(size);
        requireValidPeriod(startAt, endAt);
        List<Station> stations = stationScopeService.resolveReadableStations(stationId, consolidated, principal);
        UUID effectiveAuthor = authorshipPolicy.authorFilterOrNull(principal);
        if (effectiveAuthor == null) effectiveAuthor = authorUserId;
        return repository.findForList(stations, startAt, endAt, includeDeleted, effectiveAuthor,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("measuredAt"), Sort.Order.desc("id"))))
                .map(FlowMeasurementResponse::from);
    }

    @Transactional
    public FlowMeasurementResponse update(UUID measurementId, FlowMeasurementUpdateRequest request, UserPrincipal principal) {
        requireNotFuture(request.measuredAt());
        FlowMeasurement measurement = requireActiveMeasurement(measurementId);
        authorshipPolicy.assertOwned(principal, measurement.getCreatedBy(), NOT_FOUND_MESSAGE);
        stationScopeService.requireWritableStation(measurement.getStation(), principal);
        Station station = stationScopeService.requireWritableStation(request.stationId(), principal);
        BigDecimal heightCm = request.heightCm() == null ? measurement.getHeightCm() : request.heightCm();
        BigDecimal flowLiters = request.heightCm() == null
                ? measurement.getFlowLiters()
                : FlowRateCalculator.flowLitersFromHeightCm(request.heightCm());
        List<AuditFieldChangeDraft> changes = changesFor(measurement, station, request, heightCm, flowLiters);
        if (changes.isEmpty()) {
            throw new BusinessException("Update must change at least one field");
        }
        AppUser user = currentUserService.requireUser(principal);
        auditService.recordUpdate(
                AuditRecordType.FLOW_MEASUREMENT,
                measurement.getId(),
                measurement.getStation(),
                user,
                request.description(),
                changes
        );
        measurement.update(station, flowLiters, request.measuredAt());
        measurement.setHeightCm(heightCm);
        return FlowMeasurementResponse.from(measurement);
    }

    @Transactional
    public void delete(UUID measurementId, AuditDescriptionRequest request, UserPrincipal principal) {
        FlowMeasurement measurement = requireActiveMeasurement(measurementId);
        authorshipPolicy.assertOwned(principal, measurement.getCreatedBy(), NOT_FOUND_MESSAGE);
        stationScopeService.requireWritableStation(measurement.getStation(), principal);
        AppUser user = currentUserService.requireUser(principal);
        auditService.recordDelete(
                AuditRecordType.FLOW_MEASUREMENT,
                measurement.getId(),
                measurement.getStation(),
                user,
                request.description()
        );
        measurement.softDelete(user);
    }

    @Transactional(readOnly = true)
    public FlowMeasurementDetailResponse detail(UUID measurementId, UserPrincipal principal) {
        FlowMeasurement measurement = requireMeasurement(measurementId);
        authorshipPolicy.assertOwned(principal, measurement.getCreatedBy(), NOT_FOUND_MESSAGE);
        stationScopeService.requireReadableStation(measurement.getStation(), principal);
        List<AuditEventResponse> changes = auditService.history(AuditRecordType.FLOW_MEASUREMENT, measurementId, principal);
        boolean edited = changes.stream()
                .anyMatch(change -> change.action() == AuditAction.UPDATED);
        return new FlowMeasurementDetailResponse(
                FlowMeasurementResponse.from(measurement),
                edited,
                measurement.isDeleted(),
                principal.getRole() == UserRole.VIEWER ? List.of() : changes
        );
    }

    private FlowMeasurement requireMeasurement(UUID measurementId) {
        return repository.findById(measurementId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
    }

    private FlowMeasurement requireActiveMeasurement(UUID measurementId) {
        return repository.findByIdAndDeletedFalse(measurementId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
    }

    private void requireValidPeriod(Instant startAt, Instant endAt) {
        if (startAt != null && endAt != null && !endAt.isAfter(startAt)) {
            throw new BusinessException("Horário de fim deve ser maior que o horário de início");
        }
    }

    private void requireNotFuture(Instant timestamp) {
        if (timestamp.isAfter(Instant.now(clock))) {
            throw new BusinessException(FUTURE_TIME_MESSAGE);
        }
    }

    private List<AuditFieldChangeDraft> changesFor(
            FlowMeasurement measurement,
            Station station,
            FlowMeasurementUpdateRequest request,
            BigDecimal heightCm,
            BigDecimal flowLiters
    ) {
        List<AuditFieldChangeDraft> changes = new ArrayList<>();
        addChange(changes, "station", "ITE", measurement.getStation().getId(), station.getId(),
                measurement.getStation().getName(), station.getName());
        addChange(changes, "heightCm", "Altura (cm)", measurement.getHeightCm(), heightCm);
        addChange(changes, "flowLiters", "Vazao (m³/h)", measurement.getFlowLiters(), flowLiters);
        addChange(changes, "measuredAt", "Data da medicao", measurement.getMeasuredAt(), request.measuredAt());
        return changes;
    }

    private void addChange(List<AuditFieldChangeDraft> changes, String name, String label, Object before, Object after) {
        if (!sameValue(before, after)) {
            changes.add(new AuditFieldChangeDraft(name, label, valueOf(before), valueOf(after)));
        }
    }

    private boolean sameValue(Object before, Object after) {
        if (before instanceof BigDecimal beforeDecimal && after instanceof BigDecimal afterDecimal) {
            return beforeDecimal.compareTo(afterDecimal) == 0;
        }
        return Objects.equals(before, after);
    }

    private void addChange(
            List<AuditFieldChangeDraft> changes,
            String name,
            String label,
            UUID beforeId,
            UUID afterId,
            String before,
            String after
    ) {
        if (!Objects.equals(beforeId, afterId)) {
            changes.add(new AuditFieldChangeDraft(name, label, before, after));
        }
    }

    private String valueOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros().toPlainString();
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        return value.toString();
    }
}
