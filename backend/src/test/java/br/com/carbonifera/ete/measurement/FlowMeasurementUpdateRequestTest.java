package br.com.carbonifera.ete.measurement;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FlowMeasurementUpdateRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsMissingHeight() {
        assertThat(validator.validate(request(null))).isEmpty();
    }

    @Test
    void rejectsOutOfRangeAndOverPreciseHeight() {
        assertThat(validator.validate(request(new BigDecimal("-0.01"))))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("heightCm");
        assertThat(validator.validate(request(new BigDecimal("45.01"))))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("heightCm");
        assertThat(validator.validate(request(new BigDecimal("1.234"))))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("heightCm");
    }

    private FlowMeasurementUpdateRequest request(BigDecimal heightCm) {
        return new FlowMeasurementUpdateRequest(
                UUID.randomUUID(),
                heightCm,
                Instant.parse("2026-07-01T10:00:00Z"),
                "Correcao da vazao"
        );
    }
}
