package br.com.carbonifera.ete.security;

import br.com.carbonifera.ete.config.JwtProperties;
import br.com.carbonifera.ete.device.DeviceKey;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final long ACCESS_TOKEN_MINUTES = 15;
    private JwtService jwtService;
    private DeviceKey deviceKey;
    private UUID deviceKeyId;
    private UUID stationId;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties("jwt_secret_with_at_least_32_characters", ACCESS_TOKEN_MINUTES, 30));
        deviceKeyId = UUID.randomUUID();
        stationId = UUID.randomUUID();
        Station station = new Station("Station", "station");
        ReflectionTestUtils.setField(station, "id", stationId);
        deviceKey = new DeviceKey(station, "Arduino", "encrypted", "hash",
                new AppUser("Admin", "admin", "hash", UserRole.ADMIN, false));
        ReflectionTestUtils.setField(deviceKey, "id", deviceKeyId);
    }

    @Test
    void deviceAccessTokenHasDeviceClaimsAndConfiguredExpiration() {
        String token = jwtService.generateDeviceAccessToken(deviceKey);
        Claims claims = jwtService.parseClaims(token);
        Instant now = Instant.now();

        assertThat(claims.getSubject()).isEqualTo("device:" + deviceKeyId);
        assertThat(claims.get("role", String.class)).isEqualTo("DEVICE");
        assertThat(claims.get("deviceKeyId", String.class)).isEqualTo(deviceKeyId.toString());
        assertThat(claims.get("stationId", String.class)).isEqualTo(stationId.toString());
        assertThat(claims.get("tokenType", String.class)).isEqualTo("access");
        assertThat(claims.getExpiration().toInstant()).isBetween(
                now.plusSeconds(ACCESS_TOKEN_MINUTES * 60 - 2), now.plusSeconds(ACCESS_TOKEN_MINUTES * 60 + 2));
        assertThat(jwtService.deviceKeyId(token)).isEqualTo(deviceKeyId);
    }

    @Test
    void identifiesDeviceAccessToken() {
        assertThat(jwtService.isDeviceToken(jwtService.generateDeviceAccessToken(deviceKey))).isTrue();
    }

    @Test
    void deviceTokenRemainsAnAccessToken() {
        assertThat(jwtService.isAccessToken(jwtService.generateDeviceAccessToken(deviceKey))).isTrue();
    }

    @Test
    void userAccessTokenIsNotADeviceToken() {
        AppUser user = new AppUser("Admin", "admin", "hash", UserRole.ADMIN, false);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

        assertThat(jwtService.isDeviceToken(jwtService.generateAccessToken(user))).isFalse();
    }

    @Test
    void devicePrincipalExposesDeviceAuthorityAndIdentity() {
        DevicePrincipal principal = new DevicePrincipal(deviceKeyId, stationId, "Arduino");

        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_DEVICE");
        assertThat(principal.getDeviceKeyId()).isEqualTo(deviceKeyId);
        assertThat(principal.getStationId()).isEqualTo(stationId);
        assertThat(principal.getName()).isEqualTo("Arduino");
    }
}
