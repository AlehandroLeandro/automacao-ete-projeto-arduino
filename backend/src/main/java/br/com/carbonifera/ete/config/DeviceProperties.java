package br.com.carbonifera.ete.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.device")
public record DeviceProperties(
        String encryptionSecret,
        @Min(1) long offlineMinutes,
        @Min(1) long rateLimitPerMinute
) {
    public DeviceProperties {
        if (offlineMinutes == 0) {
            offlineMinutes = 5;
        }
        if (rateLimitPerMinute == 0) {
            rateLimitPerMinute = 10;
        }
    }
}
