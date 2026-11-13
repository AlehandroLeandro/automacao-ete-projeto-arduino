package br.com.carbonifera.ete.lime;

import br.com.carbonifera.ete.common.error.BusinessException;
import br.com.carbonifera.ete.common.error.ResourceNotFoundException;
import br.com.carbonifera.ete.common.pagination.PageSizeValidator;
import br.com.carbonifera.ete.security.CurrentUserService;
import br.com.carbonifera.ete.security.OperatorAuthorshipPolicy;
import br.com.carbonifera.ete.security.UserPrincipal;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.station.StationScopeService;
import br.com.carbonifera.ete.user.AppUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LimeService {

    private static final String INSUFFICIENT_STOCK_MESSAGE = "Estoque de cal insuficiente para este consumo";
    private static final String INVALID_QUANTITY_MESSAGE = "Quantidade de sacos deve ser maior que zero";
    private static final String NOT_FOUND_MESSAGE = "Lime record not found";

    private final LimeRecordRepository repository;
    private final LimeStockCalculator stockCalculator;
    private final StationScopeService stationScopeService;
    private final CurrentUserService currentUserService;
    private final LimeMinStockMonitor limeMinStockMonitor;
    private final OperatorAuthorshipPolicy authorshipPolicy;

    @Autowired
    public LimeService(
            LimeRecordRepository repository,
            LimeStockCalculator stockCalculator,
            StationScopeService stationScopeService,
            CurrentUserService currentUserService,
            LimeMinStockMonitor limeMinStockMonitor,
            OperatorAuthorshipPolicy authorshipPolicy
    ) {
        this.repository = repository;
        this.stockCalculator = stockCalculator;
        this.stationScopeService = stationScopeService;
        this.currentUserService = currentUserService;
        this.limeMinStockMonitor = limeMinStockMonitor;
        this.authorshipPolicy = authorshipPolicy;
    }

    LimeService(
            LimeRecordRepository repository,
            LimeStockCalculator stockCalculator,
            StationScopeService stationScopeService,
            CurrentUserService currentUserService,
            LimeMinStockMonitor limeMinStockMonitor
    ) {
        this(
                repository,
                stockCalculator,
                stationScopeService,
                currentUserService,
                limeMinStockMonitor,
                new OperatorAuthorshipPolicy()
        );
    }

    @Transactional
    public LimeRecordResponse create(LimeRecordRequest request, UserPrincipal principal) {
        validatePositiveQuantity(request.quantityBags());
        Station station = stationScopeService.requireWritableStation(request.stationId(), principal);
        validateStockProjection(station, request, null);
        AppUser user = currentUserService.requireUser(principal);
        if (request.clientRequestId() != null) {
            var existing = repository.findByClientRequestId(request.clientRequestId());
            if (existing.isPresent() && existing.get().getCreatedBy().getId().equals(user.getId())
                    && existing.get().getStation().getId().equals(station.getId())) return LimeRecordResponse.from(existing.get());
        }
        LimeRecord saved = new LimeRecord(
                station,
                request.type(),
                request.quantityBags(),
                request.recordedAt(),
                request.note(),
                user
        );
        saved.setClientRequestId(request.clientRequestId());
        saved = repository.save(saved);
        limeMinStockMonitor.evaluate(station);
        return LimeRecordResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<LimeRecordListResponse> list(
            UUID stationId,
            boolean consolidated,
            LimeRecordType type,
            Instant startAt,
            Instant endAt,
            UserPrincipal principal
    ) {
        requireValidPeriod(startAt, endAt);
        List<Station> stations = stationScopeService.resolveReadableStations(stationId, consolidated, principal);
        return repository.findForList(stations, type, startAt, endAt, authorshipPolicy.authorFilterOrNull(principal))
                .stream()
                .map(record -> LimeRecordListResponse.from(record, stockAfter(record)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<LimeRecordListResponse> list(
            UUID stationId,
            boolean consolidated,
            LimeRecordType type,
            Instant startAt,
            Instant endAt,
            UUID authorUserId,
            int page,
            int size,
            UserPrincipal principal
    ) {
        PageSizeValidator.validate(size);
        requireValidPeriod(startAt, endAt);
        List<Station> stations = stationScopeService.resolveReadableStations(stationId, consolidated, principal);
        UUID effectiveAuthor = authorshipPolicy.authorFilterOrNull(principal);
        if (effectiveAuthor == null) effectiveAuthor = authorUserId;
        return repository.findForList(stations, type, startAt, endAt, effectiveAuthor,
                        PageRequest.of(page, size, Sort.by(Sort.Order.desc("recordedAt"), Sort.Order.desc("id"))))
                .map(record -> LimeRecordListResponse.from(record, stockAfter(record)));
    }

    @Transactional
    public LimeRecordResponse update(UUID recordId, LimeRecordRequest request, UserPrincipal principal) {
        validatePositiveQuantity(request.quantityBags());
        LimeRecord record = requireRecord(recordId);
        authorshipPolicy.assertOwned(principal, record.getCreatedBy(), NOT_FOUND_MESSAGE);
        stationScopeService.requireWritableStation(record.getStation(), principal);
        Station previousStation = record.getStation();
        Station station = stationScopeService.requireWritableStation(request.stationId(), principal);
        validateStockProjection(record, station, request);
        record.update(station, request.type(), request.quantityBags(), request.recordedAt(), request.note());
        limeMinStockMonitor.evaluate(previousStation);
        if (!previousStation.getId().equals(station.getId())) {
            limeMinStockMonitor.evaluate(station);
        }
        return LimeRecordResponse.from(record);
    }

    @Transactional
    public void delete(UUID recordId, UserPrincipal principal) {
        LimeRecord record = requireRecord(recordId);
        authorshipPolicy.assertOwned(principal, record.getCreatedBy(), NOT_FOUND_MESSAGE);
        stationScopeService.requireWritableStation(record.getStation(), principal);
        validateStockProjectionWithoutRecord(record.getStation(), record.getId());
        Station station = record.getStation();
        repository.delete(record);
        limeMinStockMonitor.evaluate(station);
    }

    @Transactional(readOnly = true)
    public LimeStockResponse currentStock(UUID stationId, Instant asOf, UserPrincipal principal) {
        Station station = stationScopeService.requireReadableStation(stationId, principal);
        return currentStock(station, asOf);
    }

    @Transactional(readOnly = true)
    public LimeStockResponse currentStock(Station station, Instant asOf) {
        BigDecimal stock = stockCalculator.currentStock(stockEvents(station, asOf), asOf);
        return new LimeStockResponse(station.getId(), station.getName(), stock, asOf);
    }

    @Transactional(readOnly = true)
    public BigDecimal sumConsumption(Station station, Instant startAt, Instant endAt) {
        return repository.sumConsumption(station, startAt, endAt);
    }

    private List<LimeStockEvent> stockEvents(Station station, Instant asOf) {
        return repository.findAllByStationAndRecordedAtLessThanEqualOrderByRecordedAtAsc(station, asOf)
                .stream()
                .map(this::stockEvent)
                .toList();
    }

    private BigDecimal stockAfter(LimeRecord record) {
        return stockCalculator.currentStock(stockEvents(record.getStation(), record.getRecordedAt()), record.getRecordedAt());
    }

    private LimeRecord requireRecord(UUID recordId) {
        return repository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
    }

    private void requireValidPeriod(Instant startAt, Instant endAt) {
        if (startAt != null && endAt != null && !endAt.isAfter(startAt)) {
            throw new BusinessException("Horário de fim deve ser maior que o horário de início");
        }
    }

    private void validatePositiveQuantity(BigDecimal quantityBags) {
        if (quantityBags == null || quantityBags.signum() <= 0) {
            throw new BusinessException(INVALID_QUANTITY_MESSAGE);
        }
    }

    private void validateStockProjection(LimeRecord record, Station station, LimeRecordRequest request) {
        if (record.getStation().getId().equals(station.getId())) {
            validateStockProjection(station, request, record.getId());
            return;
        }

        validateStockProjectionWithoutRecord(record.getStation(), record.getId());
        validateStockProjection(station, request, record.getId());
    }

    private void validateStockProjection(Station station, LimeRecordRequest request, UUID replacedRecordId) {
        requireStockStaysNonNegative(projectedEvents(station, request, replacedRecordId));
    }

    private void validateStockProjectionWithoutRecord(Station station, UUID removedRecordId) {
        List<LimeStockEvent> events = repository.findAllByStationOrderByRecordedAtAsc(station)
                .stream()
                .filter(record -> !record.getId().equals(removedRecordId))
                .map(this::stockEvent)
                .toList();
        requireStockStaysNonNegative(events);
    }

    private List<LimeStockEvent> projectedEvents(Station station, LimeRecordRequest request, UUID replacedRecordId) {
        List<LimeStockEvent> events = new ArrayList<>(repository.findAllByStationOrderByRecordedAtAsc(station)
                .stream()
                .filter(record -> replacedRecordId == null || !record.getId().equals(replacedRecordId))
                .map(this::stockEvent)
                .toList());
        events.add(new LimeStockEvent(request.type(), request.quantityBags(), request.recordedAt()));
        return events;
    }

    private void requireStockStaysNonNegative(List<LimeStockEvent> events) {
        if (!stockCalculator.stockStaysNonNegative(events)) {
            throw new BusinessException(INSUFFICIENT_STOCK_MESSAGE);
        }
    }

    private LimeStockEvent stockEvent(LimeRecord record) {
        return new LimeStockEvent(record.getType(), record.getQuantityBags(), record.getRecordedAt());
    }
}
