package br.com.carbonifera.ete.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "initial-admin")
public record InitialAdminProperties(
        @NotBlank String name,
        @NotBlank String username,
        @NotBlank String password
) {
}
