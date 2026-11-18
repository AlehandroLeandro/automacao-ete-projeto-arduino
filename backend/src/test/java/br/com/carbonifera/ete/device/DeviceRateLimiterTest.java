package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.config.DeviceProperties;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class DeviceRateLimiterTest {
    @Test void limitsEachKeyAndResetsAtNextMinute() {
        class MutableClock extends Clock {
            Instant now = Instant.parse("2026-01-01T12:00:30Z");
            public ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(ZoneId zone) { return this; }
            public Instant instant() { return now; }
        }
        MutableClock clock = new MutableClock();
        DeviceRateLimiter limiter = new DeviceRateLimiter(clock, new DeviceProperties("secret", 5, 10));
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        for (int i = 0; i < 10; i++) assertThat(limiter.allow(first)).isTrue();
        assertThat(limiter.allow(first)).isFalse();
        assertThat(limiter.allow(second)).isTrue();
        clock.now = clock.now.plusSeconds(30);
        assertThat(limiter.allow(first)).isTrue();
    }
}
