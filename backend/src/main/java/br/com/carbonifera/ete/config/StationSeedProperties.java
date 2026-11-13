package br.com.carbonifera.ete.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "stations")
public record StationSeedProperties(
        @NotBlank String novoHorizonteName,
        @NotBlank String boaVistaName
) {
}
