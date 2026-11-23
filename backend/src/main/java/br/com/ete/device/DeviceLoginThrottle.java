package br.com.ete.device;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Single-process limiter. At capacity, unknown IPs are blocked until space expires;
 * live failure histories and blocks are never evicted to admit a new IP.
 * Callers must coordinate the check/authentication/result sequence per IP: the
 * individual methods are atomic, but a sequence of separate calls is not.
 */
@Component
public class DeviceLoginThrottle {

    private static final int FAILURE_LIMIT = 10;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(5);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);
    private static final int MAX_TRACKED_IPS = 10_000;

    private final Clock clock;
    private final int maxTrackedIps;
    private final Map<String, IpState> states = new HashMap<>();
    private final NavigableSet<IpState> expirations = new TreeSet<>(
            Comparator.comparing((IpState state) -> state.expiresAt).thenComparing(state -> state.ip));

    @Autowired
    public DeviceLoginThrottle(Clock clock) {
        this(clock, MAX_TRACKED_IPS);
    }

    DeviceLoginThrottle(Clock clock, int maxTrackedIps) {
        if (maxTrackedIps < 1) throw new IllegalArgumentException("maxTrackedIps must be positive");
        this.clock = clock;
        this.maxTrackedIps = maxTrackedIps;
    }

    // ponytail: a short global lock protects only bounded memory operations, never authentication I/O.
    public synchronized boolean isBlocked(String ip) {
        expire(clock.instant());
        IpState state = states.get(ip);
        return state == null ? states.size() >= maxTrackedIps : state.blocked;
    }

    public synchronized void recordFailure(String ip) {
        Instant now = clock.instant();
        expire(now);
        IpState state = states.get(ip);
        if (state == null) {
            if (states.size() >= maxTrackedIps) return;
            state = new IpState(ip);
            states.put(ip, state);
        } else if (state.blocked) {
            return;
        } else {
            expirations.remove(state);
        }

        Instant windowStart = now.minus(FAILURE_WINDOW);
        while (!state.failures.isEmpty() && !state.failures.peekFirst().isAfter(windowStart)) {
            state.failures.removeFirst();
        }
        state.failures.addLast(now);
        state.blocked = state.failures.size() >= FAILURE_LIMIT;
        state.expiresAt = now.plus(state.blocked ? BLOCK_DURATION : FAILURE_WINDOW);
        if (state.blocked) state.failures.clear();
        expirations.add(state);
    }

    public synchronized void recordSuccess(String ip) {
        expire(clock.instant());
        IpState state = states.remove(ip);
        if (state != null) expirations.remove(state);
    }

    synchronized int retainedIpCount() {
        expire(clock.instant());
        return states.size();
    }

    private void expire(Instant now) {
        while (!expirations.isEmpty() && !expirations.first().expiresAt.isAfter(now)) {
            IpState expired = expirations.pollFirst();
            states.remove(expired.ip);
        }
    }

    private static final class IpState {
        private final String ip;
        private final ArrayDeque<Instant> failures = new ArrayDeque<>();
        private Instant expiresAt;
        private boolean blocked;

        private IpState(String ip) {
            this.ip = ip;
        }
    }
}
