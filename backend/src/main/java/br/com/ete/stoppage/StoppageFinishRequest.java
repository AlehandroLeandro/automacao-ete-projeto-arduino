package br.com.ete.stoppage;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record StoppageFinishRequest(@NotNull Instant endAt) {
}
