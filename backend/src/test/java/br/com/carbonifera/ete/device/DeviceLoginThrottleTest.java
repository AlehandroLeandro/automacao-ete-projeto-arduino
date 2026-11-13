package br.com.carbonifera.ete.device;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

    @Test
    void failuresExpireExactlyAtFiveMinutesAndWithinWindowStillCount() {
        MutableClock clock = new MutableClock(START);
        DeviceLoginThrottle throttle = new DeviceLoginThrottle(clock);
        for (int i = 0; i < 9; i++) throttle.recordFailure("within");
        for (int i = 0; i < 9; i++) throttle.recordFailure("boundary");
        clock.advance(Duration.ofMinutes(5).minusNanos(1));
        throttle.recordFailure("within");
        assertThat(throttle.isBlocked("within")).isTrue();
        clock.advance(Duration.ofNanos(1));
        throttle.recordFailure("boundary");
        assertThat(throttle.isBlocked("boundary")).isFalse();
        for (int i = 0; i < 8; i++) throttle.recordFailure("boundary");
        assertThat(throttle.isBlocked("boundary")).isFalse();
        throttle.recordFailure("boundary");
        assertThat(throttle.isBlocked("boundary")).isTrue();
    }

    @Test
    void saturationRejectsNewIpsWithoutErasingExistingFailureHistoriesOrBlocks() throws Exception {
        MutableClock clock = new MutableClock(START);
        DeviceLoginThrottle throttle = new DeviceLoginThrottle(clock, 2);
        for (int i = 0; i < 10; i++) throttle.recordFailure("blocked");
        for (int i = 0; i < 9; i++) throttle.recordFailure("pending");
        for (int i = 0; i < 10_000; i++) {
            assertThat(throttle.isBlocked("flood-" + i)).isTrue();
            throttle.recordFailure("flood-" + i);
        }
        assertThat(throttle.retainedIpCount()).isEqualTo(2);
        assertThat(expirationCount(throttle)).isEqualTo(2);
        assertThat(throttle.isBlocked("blocked")).isTrue();
        assertThat(throttle.isBlocked("pending")).isFalse();
        throttle.recordFailure("pending");
        assertThat(throttle.isBlocked("pending")).isTrue();
        clock.advance(Duration.ofMinutes(15).minusNanos(1));
        assertThat(throttle.isBlocked("blocked")).isTrue();
        assertThat(throttle.isBlocked("pending")).isTrue();
        clock.advance(Duration.ofNanos(1));
        assertThat(throttle.isBlocked("new")).isFalse();
        assertThat(throttle.retainedIpCount()).isZero();
        assertThat(expirationCount(throttle)).isZero();
    }

    @Test
    void expiresIdleIpsWhilePreservingLiveBlocksAndReusesReleasedCapacity() throws Exception {
        MutableClock clock = new MutableClock(START);
        DeviceLoginThrottle throttle = new DeviceLoginThrottle(clock, 2);
        throttle.recordFailure("idle");
        for (int i = 0; i < 10; i++) throttle.recordFailure("blocked");
        clock.advance(Duration.ofMinutes(5).minusNanos(1));
        assertThat(throttle.isBlocked("new")).isTrue();
        clock.advance(Duration.ofNanos(1));
        assertThat(throttle.isBlocked("new")).isFalse();
        assertThat(throttle.retainedIpCount()).isEqualTo(1);
        assertThat(expirationCount(throttle)).isEqualTo(1);
        assertThat(throttle.isBlocked("blocked")).isTrue();
        throttle.recordFailure("new");
        assertThat(throttle.retainedIpCount()).isEqualTo(2);
        throttle.recordSuccess("new");
        assertThat(throttle.retainedIpCount()).isEqualTo(1);
        assertThat(expirationCount(throttle)).isEqualTo(1);
    }

    @Test
    void newFailureRenewsRetentionWithoutKeepingOldFailures() throws Exception {
        MutableClock clock = new MutableClock(START);
        DeviceLoginThrottle throttle = new DeviceLoginThrottle(clock, 1);
        for (int i = 0; i < 8; i++) throttle.recordFailure("rolling");
        clock.advance(Duration.ofMinutes(4));
        throttle.recordFailure("rolling");
        assertThat(expirationCount(throttle)).isEqualTo(1);
        clock.advance(Duration.ofMinutes(1));
        assertThat(throttle.isBlocked("new")).isTrue();
        throttle.recordFailure("rolling");
        assertThat(throttle.isBlocked("rolling")).isFalse();
        clock.advance(Duration.ofMinutes(5).minusNanos(1));
        assertThat(throttle.isBlocked("new")).isTrue();
        clock.advance(Duration.ofNanos(1));
        assertThat(throttle.isBlocked("new")).isFalse();
        assertThat(throttle.retainedIpCount()).isZero();
        assertThat(expirationCount(throttle)).isZero();
    }

    @Test
    void attemptsDuringBlockDoNotExtendItsDeadlineAndNextWindowStartsEmpty() {
        MutableClock clock = new MutableClock(START);
        DeviceLoginThrottle throttle = new DeviceLoginThrottle(clock);
        for (int i = 0; i < 10; i++) throttle.recordFailure("blocked");
        clock.advance(Duration.ofMinutes(14));
        for (int i = 0; i < 100; i++) throttle.recordFailure("blocked");
        clock.advance(Duration.ofMinutes(1));
        for (int i = 0; i < 9; i++) throttle.recordFailure("blocked");
        assertThat(throttle.isBlocked("blocked")).isFalse();
        throttle.recordFailure("blocked");
        assertThat(throttle.isBlocked("blocked")).isTrue();
    }

    @Test
    void expirationIndexStaysBoundedThroughRepeatedFailuresAndSuccesses() throws Exception {
        DeviceLoginThrottle throttle = new DeviceLoginThrottle(Clock.fixed(START, ZoneOffset.UTC), 2);
        for (int cycle = 0; cycle < 1_000; cycle++) {
            for (int i = 0; i < 10; i++) throttle.recordFailure("reused");
            assertThat(throttle.retainedIpCount()).isEqualTo(1);
            assertThat(expirationCount(throttle)).isEqualTo(1);
            throttle.recordSuccess("reused");
            assertThat(throttle.retainedIpCount()).isZero();
            assertThat(expirationCount(throttle)).isZero();
        }
    }

    @Test
    void parallelFailuresAreNotLostWhileExpiredEntriesAreRemoved() throws Exception {
        MutableClock clock = new MutableClock(START);
        DeviceLoginThrottle throttle = new DeviceLoginThrottle(clock, 100);
        for (int i = 0; i < 100; i++) throttle.recordFailure("expired-" + i);
        clock.advance(Duration.ofMinutes(5));
        for (int round = 0; round < 50; round++) {
            String ip = "parallel-" + round;
            runConcurrently(10, i -> throttle.recordFailure(ip));
            assertThat(throttle.isBlocked(ip)).isTrue();
        }
        assertThat(throttle.retainedIpCount()).isEqualTo(50);
        assertThat(expirationCount(throttle)).isEqualTo(50);
    }

    @Test
    void concurrentIpFloodCannotExceedCapacityOrEraseLiveBlock() throws Exception {
        DeviceLoginThrottle throttle = new DeviceLoginThrottle(Clock.fixed(START, ZoneOffset.UTC), 16);
        for (int i = 0; i < 10; i++) throttle.recordFailure("blocked");
        runConcurrently(16, worker -> {
            for (int i = 0; i < 500; i++) {
                throttle.recordFailure("flood-" + worker + "-" + i);
                throttle.isBlocked("blocked");
            }
        });
        assertThat(throttle.retainedIpCount()).isEqualTo(16);
        assertThat(expirationCount(throttle)).isEqualTo(16);
        assertThat(throttle.isBlocked("blocked")).isTrue();
        assertThat(throttle.isBlocked("unknown")).isTrue();
    }

    private static int expirationCount(DeviceLoginThrottle throttle) throws Exception {
        var field = DeviceLoginThrottle.class.getDeclaredField("expirations");
        field.setAccessible(true);
        return ((Collection<?>) field.get(throttle)).size();
    }

    private static void runConcurrently(int workers, java.util.function.IntConsumer action) throws Exception {
        var executor = Executors.newFixedThreadPool(workers);
        var start = new CountDownLatch(1);
        var ready = new CountDownLatch(workers);
        var futures = new ArrayList<Future<?>>();
        try {
            for (int i = 0; i < workers; i++) {
                int worker = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("start timed out");
                    action.accept(worker);
                    return null;
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) future.get(10, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
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
