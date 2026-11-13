package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.config.JwtProperties;
import br.com.carbonifera.ete.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeviceAuthService {

    // ponytail: fixed stripes bound memory; colliding IPs share a lock during authentication I/O.
    private static final int LOCK_COUNT = 64;
    private static final Object[] IP_LOCKS = createLocks();
    private static final String INVALID_CREDENTIALS = "Credenciais inválidas";

    private final DeviceKeyRepository repository;
    private final DeviceKeyHasher hasher;
    private final JwtService jwtService;
    private final DeviceLoginThrottle throttle;
    private final long expiresInSeconds;
    private final TransactionTemplate transaction;

    public DeviceAuthService(
            DeviceKeyRepository repository,
            DeviceKeyHasher hasher,
            JwtService jwtService,
            DeviceLoginThrottle throttle,
            JwtProperties jwtProperties,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.hasher = hasher;
        this.jwtService = jwtService;
        this.throttle = throttle;
        this.expiresInSeconds = jwtProperties.accessTokenMinutes() * 60;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setReadOnly(true);
    }

    public DeviceAuthResponse login(String key, String ip) {
        synchronized (lockFor(ip)) {
            if (throttle.isBlocked(ip)) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
            }

            String accessToken = key == null || key.length() != 24 ? null : transaction.execute(status -> {
                DeviceKey deviceKey = repository.findByValueHash(hasher.hash(key))
                        .filter(DeviceKey::isActive).orElse(null);
                return deviceKey == null ? null : jwtService.generateDeviceAccessToken(deviceKey);
            });
            if (accessToken == null) {
                throttle.recordFailure(ip);
                throw new BadCredentialsException(INVALID_CREDENTIALS);
            }

            throttle.recordSuccess(ip);
            return new DeviceAuthResponse(accessToken, "Bearer", expiresInSeconds);
        }
    }

    private static Object lockFor(String ip) {
        return IP_LOCKS[(ip == null ? 0 : ip.hashCode() & Integer.MAX_VALUE) % LOCK_COUNT];
    }

    private static Object[] createLocks() {
        Object[] locks = new Object[LOCK_COUNT];
        for (int i = 0; i < locks.length; i++) locks[i] = new Object();
        return locks;
    }
}
