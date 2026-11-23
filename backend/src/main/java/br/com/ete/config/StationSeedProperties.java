package br.com.ete.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "stations")
public record StationSeedProperties(
        @NotBlank String empresa1Name,
        @NotBlank String empresa2Name
) {
}
