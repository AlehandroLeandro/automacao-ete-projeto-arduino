package br.com.carbonifera.ete.measurement;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FlowMeasurementRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsMissingOutOfRangeAndOverPreciseHeight() {
        assertThat(validator.validate(request(null)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("heightCm");
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

    @Test
    void acceptsInclusiveHeightBoundaries() {
        assertThat(validator.validate(request(BigDecimal.ZERO))).isEmpty();
        assertThat(validator.validate(request(new BigDecimal("45")))).isEmpty();
    }

    private FlowMeasurementRequest request(BigDecimal heightCm) {
        return new FlowMeasurementRequest(UUID.randomUUID(), heightCm, Instant.parse("2026-07-01T10:00:00Z"));
    }
}
