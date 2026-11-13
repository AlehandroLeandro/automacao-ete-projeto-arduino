package br.com.carbonifera.ete.audit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record AuditDescriptionRequest(
        @NotBlank @Size(min = 10, max = 500) String description,
        Instant offlineQueuedAt
) {
    public AuditDescriptionRequest(String description) { this(description, null); }
}
