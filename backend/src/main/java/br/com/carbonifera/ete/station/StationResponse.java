package br.com.carbonifera.ete.station;

import java.math.BigDecimal;
import java.util.UUID;

public record StationResponse(
        UUID id,
        String name,
        String slug,
        boolean active,
        BigDecimal minLimeStockBags
) {

    public static StationResponse from(Station station) {
        return new StationResponse(
                station.getId(),
                station.getName(),
                station.getSlug(),
                station.isActive(),
                station.getMinLimeStockBags()
        );
    }
}
