package br.com.ete.device;

import br.com.ete.config.DeviceProperties;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class DeviceRateLimiter {
    private final Clock clock;
    private final long limit;
    private final Map<UUID, Window> windows = new HashMap<>();
    private record Window(long minute, long count) { }

    public DeviceRateLimiter(Clock clock, DeviceProperties properties) {
        this.clock = clock;
        this.limit = properties.rateLimitPerMinute();
    }

    public synchronized boolean allow(UUID keyId) {
        long minute = Math.floorDiv(clock.instant().getEpochSecond(), 60);
        windows.entrySet().removeIf(entry -> entry.getValue().minute() != minute);
        Window window = windows.get(keyId);
        long count = window == null ? 0 : window.count();
        if (count >= limit) return false;
        windows.put(keyId, new Window(minute, count + 1));
        return true;
    }
}
