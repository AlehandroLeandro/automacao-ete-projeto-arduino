package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.station.Station;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class DeviceLiveReadingService {
    private final DeviceKeyRepository keys;

    public DeviceLiveReadingService(DeviceKeyRepository keys) { this.keys = keys; }

    public record LiveReading(Station station, BigDecimal value, Instant measuredAt) { }

    @Transactional(readOnly = true)
    public Map<DeviceReadingType, LiveReading> latest(List<Station> stations) {
        Map<DeviceReadingType, LiveReading> latest = new EnumMap<>(DeviceReadingType.class);
        if (stations.isEmpty()) return latest;
        for (DeviceKey key : keys.findAllByStationIn(stations)) {
            update(latest, DeviceReadingType.FLOW, key, key.getLastFlowLiters(), key.getLastFlowAt());
            update(latest, DeviceReadingType.PH_RAW, key, key.getLastRawPh(), key.getLastRawPhAt());
            update(latest, DeviceReadingType.PH_TREATED, key, key.getLastTreatedPh(), key.getLastTreatedPhAt());
        }
        return latest;
    }

    private void update(Map<DeviceReadingType, LiveReading> latest, DeviceReadingType type, DeviceKey key,
                        BigDecimal value, Instant measuredAt) {
        if (value == null || measuredAt == null) return;
        LiveReading previous = latest.get(type);
        if (previous == null || measuredAt.isAfter(previous.measuredAt())) {
            latest.put(type, new LiveReading(key.getStation(), value, measuredAt));
        }
    }
}
