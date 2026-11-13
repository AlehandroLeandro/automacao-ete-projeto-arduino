package br.com.carbonifera.ete.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.operator")
public record OperatorAccessProperties(
        @Min(1) long recordWindowHours
) {
    public OperatorAccessProperties {
        if (recordWindowHours == 0) {
            recordWindowHours = 24;
        }
    }
}
