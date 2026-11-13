package br.com.carbonifera.ete.stoppage;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record StoppageStartRequest(
        @NotNull UUID stationId,
        @NotNull StoppageReason reason,
        @Size(min = 3, max = 200) String reasonDetail,
        @NotNull Instant startAt,
        UUID clientRequestId
) {
    public StoppageStartRequest(UUID stationId, StoppageReason reason, String reasonDetail, Instant startAt) {
        this(stationId, reason, reasonDetail, startAt, null);
    }
}
