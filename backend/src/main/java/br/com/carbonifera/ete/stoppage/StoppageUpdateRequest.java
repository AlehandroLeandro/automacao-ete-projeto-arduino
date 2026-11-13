package br.com.carbonifera.ete.stoppage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record StoppageUpdateRequest(
        @NotNull StoppageReason reason,
        @Size(min = 3, max = 200) String reasonDetail,
        @NotNull Instant startAt,
        Instant endAt,
        @NotBlank @Size(min = 10, max = 500) String description,
        Instant offlineQueuedAt
) {
    public StoppageUpdateRequest(StoppageReason reason, String reasonDetail, Instant startAt, Instant endAt, String description) {
        this(reason, reasonDetail, startAt, endAt, description, null);
    }
}
