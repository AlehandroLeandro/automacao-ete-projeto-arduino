package br.com.carbonifera.ete.device;

import java.math.BigDecimal;
import java.time.Instant;

public record DeviceReadingRequest(BigDecimal heightCm, BigDecimal rawPh, BigDecimal treatedPh, Instant measuredAt) { }
