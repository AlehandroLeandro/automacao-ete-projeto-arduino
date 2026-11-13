package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.common.error.BusinessException;
import br.com.carbonifera.ete.common.error.ResourceNotFoundException;
import br.com.carbonifera.ete.audit.AuditFieldChangeDraft;
import br.com.carbonifera.ete.audit.AuditRecordType;
import br.com.carbonifera.ete.audit.AuditService;
import br.com.carbonifera.ete.config.DeviceProperties;
import br.com.carbonifera.ete.security.CurrentUserService;
import br.com.carbonifera.ete.security.UserPrincipal;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.station.StationService;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class DeviceKeyService {

    private final DeviceKeyRepository repository;
    private final DeviceKeyGenerator generator;
    private final DeviceKeyCipher cipher;
    private final DeviceKeyHasher hasher;
    private final StationService stationService;
    private final CurrentUserService currentUserService;
    private final DeviceProperties properties;
    private final Clock clock;
    private final AuditService auditService;

    public DeviceKeyService(DeviceKeyRepository repository, DeviceKeyGenerator generator,
                            DeviceKeyCipher cipher, DeviceKeyHasher hasher, StationService stationService,
                            CurrentUserService currentUserService, DeviceProperties properties, Clock clock,
                            AuditService auditService) {
        this.repository = repository;
        this.generator = generator;
        this.cipher = cipher;
        this.hasher = hasher;
        this.stationService = stationService;
        this.currentUserService = currentUserService;
        this.properties = properties;
        this.clock = clock;
        this.auditService = auditService;
    }

    @Transactional
    public DeviceKeyCreatedResponse create(String name, UUID stationId, UserPrincipal principal) {
        Station station = stationService.requireStation(stationId);
        String normalizedName = name.toLowerCase(java.util.Locale.ROOT);
        if (repository.existsByStationAndNameKey(station, normalizedName)) {
            throw new BusinessException("Device key name already exists", HttpStatus.CONFLICT);
        }

        String value = generator.generate();
        DeviceKey key;
        try {
            key = repository.save(new DeviceKey(station, name, cipher.encrypt(value),
                    hasher.hash(value), currentUserService.requireUser(principal)));
            // Detect the constraint before returning; the escaping exception rolls back this transaction.
            repository.flush();
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "23505".equals(violation.getSQLState())
                        && isStationNameConstraint(violation.getConstraintName())) {
                    throw new BusinessException("Device key name already exists", HttpStatus.CONFLICT);
                }
            }
            throw exception;
        }
        return new DeviceKeyCreatedResponse(DeviceKeyResponse.from(key, status(key.getLastSeenAt(), clock.instant(), properties.offlineMinutes())), value);
    }

    @Transactional(readOnly = true)
    public List<DeviceKeyResponse> list() {
        Instant now = clock.instant();
        return repository.findAllByOrderByNameKeyAsc().stream()
                .map(key -> DeviceKeyResponse.from(key, status(key.getLastSeenAt(), now, properties.offlineMinutes())))
                .toList();
    }

    @Transactional(readOnly = true)
    public DeviceKeySecretResponse secret(UUID id) {
        DeviceKey key = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Device key not found"));
        try {
            return new DeviceKeySecretResponse(cipher.decrypt(key.getEncryptedValue()));
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Unable to retrieve device key");
        }
    }

    @Transactional
    public DeviceKeyResponse setActive(UUID id, boolean active, UserPrincipal admin) {
        DeviceKey key = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Device key not found"));
        boolean previous = key.isActive();
        if (active) key.activate(); else key.deactivate();
        auditService.recordUpdate(AuditRecordType.DEVICE_KEY, id, key.getStation(),
                currentUserService.requireUser(admin),
                active ? "Device key reactivated" : "Device key deactivated",
                List.of(new AuditFieldChangeDraft("active", "Active",
                        Boolean.toString(previous), Boolean.toString(active))));
        return DeviceKeyResponse.from(key, status(key.getLastSeenAt(), clock.instant(), properties.offlineMinutes()));
    }

    private DeviceKeyStatus status(Instant lastSeenAt, Instant now, long offlineMinutes) {
        if (lastSeenAt == null) return DeviceKeyStatus.NEVER;
        return Duration.between(lastSeenAt, now).compareTo(Duration.ofMinutes(offlineMinutes)) <= 0
                ? DeviceKeyStatus.ONLINE : DeviceKeyStatus.OFFLINE;
    }

    private boolean isStationNameConstraint(String constraintName) {
        if (constraintName == null) return false;
        // H2 reports the backing index followed by its column/value description.
        String identifier = constraintName.split(" ", 2)[0];
        return identifier.matches("(?i)(?:[a-z0-9_]+\\.)?uk_device_key_station_name(?:_index_[0-9]+)?");
    }
}
