package br.com.carbonifera.ete.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.LocalTime;
import java.time.ZoneId;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull ZoneId timezone,
        @NotNull LocalTime shiftDayStart,
        @NotNull LocalTime shiftNightStart
) {
}
