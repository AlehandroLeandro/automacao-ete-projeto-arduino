package br.com.carbonifera.ete.station;

import java.util.UUID;

public record StationScope(
        UUID stationId,
        boolean consolidated
) {
}
