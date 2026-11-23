package br.com.ete.device;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "server.port=0",
        "security.jwt.secret=context-test-jwt-secret-at-least-32-characters",
        "security.jwt.access-token-minutes=20",
        "security.jwt.refresh-token-days=7",
        "initial-admin.name=Context test admin",
        "initial-admin.username=context-test-admin",
        "initial-admin.password=context-test-admin-password",
        "DB_PASSWORD=context-test-database-password"
})
@ActiveProfiles("test")
class DeviceAuthApplicationContextTest {

    @Autowired ApplicationContext context;

    @Test
    void productionContextWiresAuthenticationToRealThrottle() {
        DeviceAuthService service = context.getBean(DeviceAuthService.class);
        DeviceLoginThrottle throttle = context.getBean(DeviceLoginThrottle.class);
        String ip = "192.0.2.233";

        assertThat(throttle.getClass()).isEqualTo(DeviceLoginThrottle.class);
        assertThat(throttle.isBlocked(ip)).isFalse();
        for (int attempt = 0; attempt < 10; attempt++) {
            assertThatThrownBy(() -> service.login(null, ip))
                    .isInstanceOf(BadCredentialsException.class)
                    .hasMessage("Credenciais inválidas");
        }
        assertThat(throttle.isBlocked(ip)).isTrue();
        assertThatThrownBy(() -> service.login(null, ip))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }
}
