package br.com.carbonifera.ete.device;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceLoginThrottleTest {
    private static final Instant START = Instant.parse("2026-11-13T12:00:00Z");

    @Test
    void blocksOnTenthFailureAndReleasesAfterFifteenMinutes() {
        MutableClock clock = new MutableClock(START);
        DeviceLoginThrottle throttle = new DeviceLoginThrottle(clock);
        for (int i = 0; i < 9; i++) throttle.recordFailure("192.0.2.1");
        assertThat(throttle.isBlocked("192.0.2.1")).isFalse();
        throttle.recordFailure("192.0.2.1");
        assertThat(throttle.isBlocked("192.0.2.1")).isTrue();
        clock.advance(Duration.ofMinutes(15).minusNanos(1));
        assertThat(throttle.isBlocked("192.0.2.1")).isTrue();
        clock.advance(Duration.ofNanos(1));
        assertThat(throttle.isBlocked("192.0.2.1")).isFalse();
    }

    @Test
    void failuresOlderThanFiveMinutesDoNotCount() {
        MutableClock clock = new MutableClock(START);
        DeviceLoginThrottle throttle = new DeviceLoginThrottle(clock);
        for (int i = 0; i < 9; i++) throttle.recordFailure("192.0.2.1");
        clock.advance(Duration.ofMinutes(5).plusNanos(1));
        throttle.recordFailure("192.0.2.1");
        assertThat(throttle.isBlocked("192.0.2.1")).isFalse();
    }

    @Test
    void successClearsFailures() {
        DeviceLoginThrottle throttle = new DeviceLoginThrottle(Clock.fixed(START, ZoneOffset.UTC));
        for (int i = 0; i < 9; i++) throttle.recordFailure("192.0.2.1");
        throttle.recordSuccess("192.0.2.1");
        for (int i = 0; i < 9; i++) throttle.recordFailure("192.0.2.1");
        assertThat(throttle.isBlocked("192.0.2.1")).isFalse();
    }

    @Test
    void differentIpsHaveIndependentFailureCounts() {
        DeviceLoginThrottle throttle = new DeviceLoginThrottle(Clock.fixed(START, ZoneOffset.UTC));
        for (int i = 0; i < 10; i++) throttle.recordFailure("192.0.2.1");
        for (int i = 0; i < 9; i++) throttle.recordFailure("192.0.2.2");
        assertThat(throttle.isBlocked("192.0.2.1")).isTrue();
        assertThat(throttle.isBlocked("192.0.2.2")).isFalse();
    }

    @Test
    void successReleasesBlockedIp() {
        DeviceLoginThrottle throttle = new DeviceLoginThrottle(Clock.fixed(START, ZoneOffset.UTC));
        for (int i = 0; i < 10; i++) throttle.recordFailure("192.0.2.1");
        throttle.recordSuccess("192.0.2.1");
        assertThat(throttle.isBlocked("192.0.2.1")).isFalse();
        for (int i = 0; i < 9; i++) throttle.recordFailure("192.0.2.1");
        assertThat(throttle.isBlocked("192.0.2.1")).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        private void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
        @Override public Instant instant() { return instant; }
    }
}
