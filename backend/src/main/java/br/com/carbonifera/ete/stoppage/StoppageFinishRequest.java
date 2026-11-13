package br.com.carbonifera.ete.stoppage;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record StoppageFinishRequest(@NotNull Instant endAt) {
}
