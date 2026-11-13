package br.com.carbonifera.ete.device;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

public class DeviceLoginThrottle {

    private static final int FAILURE_LIMIT = 10;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(5);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    private final Clock clock;
    private final ConcurrentHashMap<String, IpState> states = new ConcurrentHashMap<>();

    public DeviceLoginThrottle(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String ip) {
        IpState state = states.get(ip);
        if (state == null) {
            return false;
        }
        synchronized (state) {
            if (state.blockedUntil == null) {
                return false;
            }
            if (clock.instant().isBefore(state.blockedUntil)) {
                return true;
            }
            state.blockedUntil = null;
            state.failures.clear();
            return false;
        }
    }

    public void recordFailure(String ip) {
        IpState state = states.computeIfAbsent(ip, ignored -> new IpState());
        synchronized (state) {
            Instant now = clock.instant();
            if (state.blockedUntil != null) {
                if (now.isBefore(state.blockedUntil)) {
                    return;
                }
                state.blockedUntil = null;
                state.failures.clear();
            }

            Instant windowStart = now.minus(FAILURE_WINDOW);
            while (!state.failures.isEmpty() && !state.failures.peekFirst().isAfter(windowStart)) {
                state.failures.removeFirst();
            }
            state.failures.addLast(now);
            if (state.failures.size() >= FAILURE_LIMIT) {
                state.blockedUntil = now.plus(BLOCK_DURATION);
            }
        }
    }

    public void recordSuccess(String ip) {
        IpState state = states.get(ip);
        if (state == null) {
            return;
        }
        synchronized (state) {
            state.failures.clear();
            state.blockedUntil = null;
        }
    }

    private static final class IpState {
        private final ArrayDeque<Instant> failures = new ArrayDeque<>();
        private Instant blockedUntil;
    }
}
