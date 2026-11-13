package br.com.carbonifera.ete.security;

import br.com.carbonifera.ete.device.DeviceKey;
import br.com.carbonifera.ete.config.JwtProperties;
import br.com.carbonifera.ete.user.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final String TOKEN_TYPE = "tokenType";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(AppUser user) {
        Instant expiresAt = Instant.now().plus(properties.accessTokenMinutes(), ChronoUnit.MINUTES);
        return generateToken(user, ACCESS, expiresAt);
    }

    public String generateDeviceAccessToken(DeviceKey deviceKey) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.accessTokenMinutes(), ChronoUnit.MINUTES);
        UUID deviceKeyId = deviceKey.getId();
        UUID stationId = deviceKey.getStation().getId();
        return Jwts.builder()
                .subject("device:" + deviceKeyId)
                .claim("role", "DEVICE")
                .claim("deviceKeyId", deviceKeyId.toString())
                .claim("stationId", stationId.toString())
                .claim(TOKEN_TYPE, ACCESS)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(AppUser user) {
        Instant expiresAt = Instant.now().plus(properties.refreshTokenDays(), ChronoUnit.DAYS);
        return generateToken(user, REFRESH, expiresAt);
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isRefreshToken(String token) {
        return REFRESH.equals(parseClaims(token).get(TOKEN_TYPE, String.class));
    }

    public boolean isAccessToken(String token) {
        return ACCESS.equals(parseClaims(token).get(TOKEN_TYPE, String.class));
    }

    public boolean isDeviceToken(String token) {
        Claims claims = parseClaims(token);
        return "DEVICE".equals(claims.get("role", String.class))
                && ACCESS.equals(claims.get(TOKEN_TYPE, String.class));
    }

    public UUID deviceKeyId(String token) {
        return UUID.fromString(parseClaims(token).get("deviceKeyId", String.class));
    }

    public String subject(String token) {
        return parseClaims(token).getSubject();
    }

    private String generateToken(AppUser user, String type, Instant expiresAt) {
        Instant issuedAt = Instant.now();
        JwtBuilder builder = Jwts.builder()
                .subject(user.getUsername())
                .claim("userId", user.getId().toString())
                .claim("name", user.getName())
                .claim("role", user.getRole().name())
                .claim("changePassword", user.isChangePassword())
                .claim(TOKEN_TYPE, type)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt));

        if (user.getStation() != null) {
            builder.claim("stationId", user.getStation().getId().toString());
            builder.claim("stationName", user.getStation().getName());
        }

        return builder.signWith(key).compact();
    }
}
