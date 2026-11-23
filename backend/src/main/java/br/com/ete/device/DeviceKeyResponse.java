package br.com.ete.device;

import java.time.Instant;
import java.util.UUID;

public record DeviceKeyResponse(UUID id, String name, UUID stationId, String stationName,
                                boolean active, Instant createdAt, Instant lastSeenAt,
                                DeviceKeyStatus status) {
    static DeviceKeyResponse from(DeviceKey key, DeviceKeyStatus status) {
        return new DeviceKeyResponse(key.getId(), key.getName(), key.getStation().getId(),
                key.getStation().getName(), key.isActive(), key.getCreatedAt(), key.getLastSeenAt(), status);
    }
}
