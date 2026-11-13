package br.com.carbonifera.ete.stoppage;

import br.com.carbonifera.ete.audit.AuditAction;
import br.com.carbonifera.ete.audit.AuditDescriptionRequest;
import br.com.carbonifera.ete.audit.AuditEventResponse;
import br.com.carbonifera.ete.audit.AuditFieldChangeDraft;
import br.com.carbonifera.ete.audit.AuditRecordType;
import br.com.carbonifera.ete.audit.AuditService;
import br.com.carbonifera.ete.common.error.BusinessException;
import br.com.carbonifera.ete.common.error.ResourceNotFoundException;
import br.com.carbonifera.ete.common.pagination.PageSizeValidator;
import br.com.carbonifera.ete.config.SchedulingConfig;
import br.com.carbonifera.ete.notification.NotificationCommand;
import br.com.carbonifera.ete.notification.NotificationDispatcher;
import br.com.carbonifera.ete.notification.NotificationRecipientResolver;
import br.com.carbonifera.ete.notification.NotificationType;
import br.com.carbonifera.ete.security.CurrentUserService;
import br.com.carbonifera.ete.security.OperatorWindowPolicy;
import br.com.carbonifera.ete.security.UserPrincipal;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.station.StationScopeService;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class StoppageService {

    private static final String FUTURE_TIME_MESSAGE = "O horário não pode ser maior que o horário atual";
    private static final String OVERLAP_MESSAGE = "Já existe uma parada registrada para esta ETE nesse intervalo";
    private static final String REASON_DETAIL_REQUIRED_MESSAGE = "Descreva o motivo da parada quando selecionar \"Outros\"";
    private static final String ACTIVE_STOPPAGE_MESSAGE = "Station already has an active stoppage";
    private static final Instant OPEN_ENDED_SENTINEL = Instant.parse("9999-12-31T23:59:59Z");
    private static final DateTimeFormatter HOUR_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final StoppageRepository repository;
    private final StationScopeService stationScopeService;
    private final CurrentUserService currentUserService;
    private final ShiftCalculator shiftCalculator;
    private final AuditService auditService;
    private final NotificationDispatcher notificationDispatcher;
    private final NotificationRecipientResolver recipientResolver;
    private final Clock clock;
    private final OperatorWindowPolicy operatorWindowPolicy;

    @Autowired
    public StoppageService(
            StoppageRepository repository,
            StationScopeService stationScopeService,
            CurrentUserService currentUserService,
            ShiftCalculator shiftCalculator,
            AuditService auditService,
            NotificationDispatcher notificationDispatcher,
            NotificationRecipientResolver recipientResolver,
            OperatorWindowPolicy operatorWindowPolicy
    ) {
        this(
                repository,
                stationScopeService,
                currentUserService,
                shiftCalculator,
                auditService,
                notificationDispatcher,
                recipientResolver,
                Clock.systemUTC(),
                operatorWindowPolicy
        );
    }

    StoppageService(
            StoppageRepository repository,
            StationScopeService stationScopeService,
            CurrentUserService currentUserService,
            ShiftCalculator shiftCalculator,
            AuditService auditService,
            NotificationDispatcher notificationDispatcher,
            NotificationRecipientResolver recipientResolver
    ) {
        this(
                repository,
                stationScopeService,
                currentUserService,
                shiftCalculator,
                auditService,
                notificationDispatcher,
                recipientResolver,
                Clock.systemUTC()
        );
    }

    StoppageService(
            StoppageRepository repository,
            StationScopeService stationScopeService,
            CurrentUserService currentUserService,
            ShiftCalculator shiftCalculator,
            AuditService auditService,
            NotificationDispatcher notificationDispatcher,
            NotificationRecipientResolver recipientResolver,
            Clock clock
    ) {
        this(
                repository,
                stationScopeService,
                currentUserService,
                shiftCalculator,
                auditService,
                notificationDispatcher,
                recipientResolver,
                clock,
                OperatorWindowPolicy.unrestricted()
        );
    }

    StoppageService(
            StoppageRepository repository,
            StationScopeService stationScopeService,
            CurrentUserService currentUserService,
            ShiftCalculator shiftCalculator,
            AuditService auditService,
            NotificationDispatcher notificationDispatcher,
            NotificationRecipientResolver recipientResolver,
            Clock clock,
            OperatorWindowPolicy operatorWindowPolicy
    ) {
        this.repository = repository;
        this.stationScopeService = stationScopeService;
        this.currentUserService = currentUserService;
        this.shiftCalculator = shiftCalculator;
        this.auditService = auditService;
        this.notificationDispatcher = notificationDispatcher;
        this.recipientResolver = recipientResolver;
        this.clock = clock;
        this.operatorWindowPolicy = operatorWindowPolicy;
    }

    @Transactional
    public StoppageResponse start(StoppageStartRequest request, UserPrincipal principal) {
        requireNotFuture(request.startAt());
        Station station = stationScopeService.requireWritableStation(request.stationId(), principal);
        requireNoOverlap(station.getId(), request.startAt(), null, null);
        repository.findFirstByStationAndStatusAndDeletedFalseOrderByStartAtDesc(station, StoppageStatus.ACTIVE)
                .ifPresent(active -> {
                    throw new BusinessException(ACTIVE_STOPPAGE_MESSAGE);
                });

        AppUser user = currentUserService.requireUser(principal);
        if (request.clientRequestId() != null) {
            var existing = repository.findByClientRequestId(request.clientRequestId());
            if (existing.isPresent() && existing.get().getCreatedBy().getId().equals(user.getId())
                    && existing.get().getStation().getId().equals(station.getId())) return StoppageResponse.from(existing.get(), now());
        }
        String reasonDetail = normalizeReasonDetail(request.reason(), request.reasonDetail());
        Stoppage saved = new Stoppage(station, request.reason(), reasonDetail, request.startAt(), user);
        saved.setClientRequestId(request.clientRequestId());
        try {
            saved = repository.save(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ACTIVE_STOPPAGE_MESSAGE);
        }
        dispatchStoppageAlert(saved);
        return StoppageResponse.from(saved, now());
    }

    @Transactional
    public StoppageResponse finish(UUID stoppageId, StoppageFinishRequest request, UserPrincipal principal) {
        requireNotFuture(request.endAt());
        Stoppage stoppage = requireStoppage(stoppageId);
        stationScopeService.requireWritableStation(stoppage.getStation(), principal);
        requireNoOverlap(stoppage.getStation().getId(), stoppage.getStartAt(), request.endAt(), stoppage.getId());
        stoppage.finish(request.endAt());
        return StoppageResponse.from(stoppage, now());
    }

    @Transactional
    public StoppageResponse update(UUID stoppageId, StoppageUpdateRequest request, UserPrincipal principal) {
        requireNotFuture(request.startAt());
        requireNotFuture(request.endAt());
        Stoppage stoppage = requireStoppage(stoppageId);
        stationScopeService.requireWritableStation(stoppage.getStation(), principal);
        operatorWindowPolicy.assertWithinWindow(principal, stoppage.getStartAt(), request.offlineQueuedAt());
        operatorWindowPolicy.assertWithinWindow(principal, request.startAt(), request.offlineQueuedAt());
        requireNoOverlap(stoppage.getStation().getId(), request.startAt(), request.endAt(), stoppage.getId());
        String reasonDetail = normalizeReasonDetail(request.reason(), request.reasonDetail());
        List<AuditFieldChangeDraft> changes = changesFor(stoppage, request, reasonDetail);
        if (changes.isEmpty()) {
            throw new BusinessException("Update must change at least one field");
        }

        stoppage.updateManual(request.reason(), reasonDetail, request.startAt(), request.endAt());
        AppUser user = currentUserService.requireUser(principal);
        auditService.recordUpdate(
                AuditRecordType.STOPPAGE,
                stoppage.getId(),
                stoppage.getStation(),
                user,
                request.description(),
                changes
        );
        return StoppageResponse.from(stoppage, now());
    }

    @Transactional
    public void delete(UUID stoppageId, AuditDescriptionRequest request, UserPrincipal principal) {
        Stoppage stoppage = requireStoppage(stoppageId);
        stationScopeService.requireWritableStation(stoppage.getStation(), principal);
        operatorWindowPolicy.assertWithinWindow(principal, stoppage.getStartAt(), request.offlineQueuedAt());
        AppUser user = currentUserService.requireUser(principal);
        stoppage.softDelete(user);
        auditService.recordDelete(
                AuditRecordType.STOPPAGE,
                stoppage.getId(),
                stoppage.getStation(),
                user,
                request.description()
        );
    }

    @Transactional(readOnly = true)
    public List<StoppageResponse> active(UUID stationId, UserPrincipal principal) {
        List<Station> stations = stationScopeService.resolveReadableStations(stationId, stationId == null, principal);
        return repository.findAllByStationInAndStatusAndDeletedFalse(stations, StoppageStatus.ACTIVE)
                .stream()
                .map(stoppage -> StoppageResponse.from(stoppage, now()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StoppageResponse> list(
            UUID stationId,
            boolean consolidated,
            StoppageStatus status,
            Instant startAt,
            Instant endAt,
            boolean includeDeleted,
            UserPrincipal principal
    ) {
        requireValidPeriod(startAt, endAt);
        List<Station> stations = stationScopeService.resolveReadableStations(stationId, consolidated, principal);
        Instant reference = now();
        return findForList(stations, status, startAt, endAt, includeDeleted, principal)
                .stream()
                .map(stoppage -> StoppageResponse.from(stoppage, reference))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<StoppageResponse> list(
            UUID stationId,
            boolean consolidated,
            StoppageStatus status,
            Instant startAt,
            Instant endAt,
            boolean includeDeleted,
            UUID authorUserId,
            StoppageReason reason,
            int page,
            int size,
            UserPrincipal principal
    ) {
        PageSizeValidator.validate(size);
        requireValidPeriod(startAt, endAt);
        List<Station> stations = stationScopeService.resolveReadableStations(stationId, consolidated, principal);
        Instant reference = now();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("startAt"), Sort.Order.desc("id")));
        return findForList(stations, status, startAt, endAt, includeDeleted, authorUserId, reason, principal, pageable)
                .map(stoppage -> StoppageResponse.from(stoppage, reference));
    }

    @Transactional(readOnly = true)
    public StoppageDetailResponse detail(UUID stoppageId, UserPrincipal principal) {
        Stoppage stoppage = repository.findById(stoppageId)
                .orElseThrow(() -> new ResourceNotFoundException("Stoppage not found"));
        stationScopeService.requireReadableStation(stoppage.getStation(), principal);
        assertReadableStoppageWindow(stoppage, principal);
        List<AuditEventResponse> history = auditService.history(AuditRecordType.STOPPAGE, stoppageId, principal);
        boolean edited = history.stream().anyMatch(event -> event.action() == AuditAction.UPDATED);
        return new StoppageDetailResponse(
                StoppageResponse.from(stoppage, now()),
                edited,
                stoppage.isDeleted(),
                principal.getRole() == UserRole.VIEWER ? List.of() : history
        );
    }

    @Transactional(readOnly = true)
    public List<StoppageShiftSliceResponse> shiftSlices(UUID stoppageId, UserPrincipal principal) {
        Stoppage stoppage = requireStoppage(stoppageId);
        stationScopeService.requireReadableStation(stoppage.getStation(), principal);
        assertReadableStoppageWindow(stoppage, principal);
        Instant endAt = stoppage.getEndAt() == null ? now() : stoppage.getEndAt();
        return shiftCalculator.splitByShift(stoppage.getStartAt(), endAt)
                .stream()
                .map(StoppageShiftSliceResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Duration downtimeBetween(UUID stationId, Instant startAt, Instant endAt) {
        return repository.findOverlapping(startAt, endAt, stationId)
                .stream()
                .map(stoppage -> overlapDuration(stoppage, startAt, endAt))
                .reduce(Duration.ZERO, Duration::plus);
    }

    @Transactional(readOnly = true)
    public List<Stoppage> overlapping(UUID stationId, Instant startAt, Instant endAt) {
        return repository.findOverlapping(startAt, endAt, stationId);
    }

    private Stoppage requireStoppage(UUID stoppageId) {
        return repository.findByIdAndDeletedFalse(stoppageId)
                .orElseThrow(() -> new ResourceNotFoundException("Stoppage not found"));
    }

    private List<Stoppage> findForList(
            List<Station> stations,
            StoppageStatus status,
            Instant startAt,
            Instant endAt,
            boolean includeDeleted,
            UserPrincipal principal
    ) {
        return operatorWindowPolicy.listLowerBound(principal)
                .map(lowerBound -> {
                    Instant effectiveStartAt = lowerBound.isAfter(startAt) ? lowerBound : startAt;
                    if (!endAt.isAfter(effectiveStartAt)) {
                        return List.<Stoppage>of();
                    }
                    return repository.findForOperatorList(stations, status, effectiveStartAt, endAt, includeDeleted);
                })
                .orElseGet(() -> repository.findForList(stations, status, startAt, endAt, includeDeleted));
    }

    private Page<Stoppage> findForList(
            List<Station> stations,
            StoppageStatus status,
            Instant startAt,
            Instant endAt,
            boolean includeDeleted,
            UUID authorUserId,
            StoppageReason reason,
            UserPrincipal principal,
            Pageable pageable
    ) {
        return operatorWindowPolicy.listLowerBound(principal)
                .map(lowerBound -> {
                    Instant effectiveStartAt = lowerBound.isAfter(startAt) ? lowerBound : startAt;
                    if (!endAt.isAfter(effectiveStartAt)) return Page.<Stoppage>empty(pageable);
                    return repository.findForOperatorList(stations, status, effectiveStartAt, endAt, includeDeleted, pageable);
                })
                .orElseGet(() -> repository.findForList(stations, status, startAt, endAt, includeDeleted, authorUserId, reason, pageable));
    }

    private void assertReadableStoppageWindow(Stoppage stoppage, UserPrincipal principal) {
        if (stoppage.getEndAt() == null && !stoppage.isDeleted()) {
            return;
        }
        operatorWindowPolicy.assertWithinWindow(principal, stoppage.getStartAt());
    }

    private void requireValidPeriod(Instant startAt, Instant endAt) {
        if (startAt != null && endAt != null && !endAt.isAfter(startAt)) {
            throw new BusinessException("Horário de fim deve ser maior que o horário de início");
        }
    }

    private void requireNoOverlap(UUID stationId, Instant candidateStart, Instant candidateEnd, UUID ignoredStoppageId) {
        Instant effectiveEnd = candidateEnd == null ? OPEN_ENDED_SENTINEL : candidateEnd;
        boolean hasConflict = !repository
                .findOverlapConflicts(stationId, candidateStart, effectiveEnd, OPEN_ENDED_SENTINEL, ignoredStoppageId)
                .isEmpty();
        if (hasConflict) {
            throw new BusinessException(OVERLAP_MESSAGE);
        }
    }

    private void requireNotFuture(Instant timestamp) {
        if (timestamp != null && timestamp.isAfter(now())) {
            throw new BusinessException(FUTURE_TIME_MESSAGE);
        }
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private void dispatchStoppageAlert(Stoppage stoppage) {
        Station station = stoppage.getStation();
        String startTime = LocalTime.ofInstant(stoppage.getStartAt(), SchedulingConfig.APP_ZONE)
                .format(HOUR_FORMATTER);
        notificationDispatcher.dispatch(new NotificationCommand(
                NotificationType.STOPPAGE_ALERT,
                recipientResolver.stoppageAlertRecipients(station),
                station,
                "PARADA " + stationNotificationName(station),
                "Motivo: " + StoppageReasonLabels.describe(stoppage.getReason(), stoppage.getReasonDetail())
                        + ", Início: " + startTime,
                "/app/dashboard?station=" + station.getId(),
                null,
                stoppage.getStartAt()
        ));
    }

    private String stationNotificationName(Station station) {
        return station.getName().startsWith("ETE ") ? station.getName() : "ETE " + station.getName();
    }

    private String normalizeReasonDetail(StoppageReason reason, String reasonDetail) {
        if (reason != StoppageReason.OUTROS) {
            return null;
        }
        String normalized = reasonDetail == null ? "" : reasonDetail.trim();
        if (normalized.isEmpty()) {
            throw new BusinessException(REASON_DETAIL_REQUIRED_MESSAGE);
        }
        return normalized;
    }

    private List<AuditFieldChangeDraft> changesFor(Stoppage stoppage, StoppageUpdateRequest request, String reasonDetail) {
        List<AuditFieldChangeDraft> changes = new ArrayList<>();
        addChange(changes, "reason", "Motivo", stoppage.getReason(), request.reason());
        addChange(changes, "reasonDetail", "Descrição do motivo", stoppage.getReasonDetail(), reasonDetail);
        addChange(changes, "startAt", "Inicio", stoppage.getStartAt(), request.startAt());
        addChange(changes, "endAt", "Fim", stoppage.getEndAt(), request.endAt());
        return changes;
    }

    private void addChange(
            List<AuditFieldChangeDraft> changes,
            String name,
            String label,
            Object beforeValue,
            Object afterValue
    ) {
        if (Objects.equals(beforeValue, afterValue)) {
            return;
        }
        changes.add(new AuditFieldChangeDraft(name, label, stringValue(beforeValue), stringValue(afterValue)));
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private Duration overlapDuration(Stoppage stoppage, Instant periodStart, Instant periodEnd) {
        Instant effectiveEnd = stoppage.getEndAt() == null ? now() : stoppage.getEndAt();
        Instant start = stoppage.getStartAt().isAfter(periodStart) ? stoppage.getStartAt() : periodStart;
        Instant end = effectiveEnd.isBefore(periodEnd) ? effectiveEnd : periodEnd;
        if (!end.isAfter(start)) {
            return Duration.ZERO;
        }
        return Duration.between(start, end);
    }
}
