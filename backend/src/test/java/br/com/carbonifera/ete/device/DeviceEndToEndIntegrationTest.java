package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.measurement.FlowMeasurement;
import br.com.carbonifera.ete.measurement.FlowMeasurementRepository;
import br.com.carbonifera.ete.security.JwtService;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"server.port=0", "security.jwt.secret=context-test-jwt-secret-at-least-32-characters",
        "security.jwt.access-token-minutes=20", "security.jwt.refresh-token-days=7",
        "initial-admin.name=Test admin", "initial-admin.username=e2e-initial-admin",
        "initial-admin.password=context-test-admin-password", "DB_PASSWORD=test-password"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(DeviceEndToEndIntegrationTest.ClockConfig.class)
class DeviceEndToEndIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JwtService jwt;
    @Autowired MutableClock clock;
    @Autowired DeviceHourlyScheduler scheduler;
    @Autowired DeviceReadingRepository readings;
    @Autowired FlowMeasurementRepository flows;
    @Autowired DeviceKeyRepository keys;

    @Test
    void deviceLifecycleAggregatesAndEnforcesScope() throws Exception {
        Instant start = Instant.parse("2026-11-13T17:00:00Z");
        clock.now = start;
        String suffix = UUID.randomUUID().toString();
        String[] auth = new TransactionTemplate(transactions).execute(status -> {
            Station station = new Station("ETE A " + suffix, "a-" + suffix);
            Station other = new Station("ETE B " + suffix, "b-" + suffix);
            em.persist(station); em.persist(other);
            AppUser admin = new AppUser("Admin", "admin-" + suffix, "hash", UserRole.ADMIN, false);
            AppUser operator = new AppUser("Operator", "op-" + suffix, "hash", UserRole.OPERATOR, station, false);
            AppUser otherOperator = new AppUser("Other", "other-" + suffix, "hash", UserRole.OPERATOR, other, false);
            em.persist(admin); em.persist(operator); em.persist(otherOperator); em.flush();
            return new String[]{station.getId().toString(), jwt.generateAccessToken(admin),
                    jwt.generateAccessToken(operator), jwt.generateAccessToken(otherOperator)};
        });
        JsonNode created = request(post("/api/device-keys").header("Authorization", bearer(auth[1]))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name", "Sensor E2E", "stationId", auth[0]))));
        String id = created.path("id").asText();
        String secret = request(get("/api/device-keys/" + id + "/secret").header("Authorization", bearer(auth[1]))).path("value").asText();
        JsonNode login = request(post("/api/auth/device-login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("key", secret))));
        String device = bearer(login.path("accessToken").asText());
        assertThat(login.path("tokenType").asText()).isEqualToIgnoringCase("Bearer");
        assertThat(login.path("expiresInSeconds").asLong()).isPositive();
        for (int minute = 0; minute < 60; minute++) {
            clock.now = start.plusSeconds(minute * 60L);
            JsonNode response = request(post("/api/device/readings").header("Authorization", device)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"heightCm\":" + (minute % 2 == 0 ? 10 : 20) + ",\"rawPh\":7,\"treatedPh\":8}"));
            assertThat(response.path("accepted")).hasSize(3);
            assertThat(response.path("rejected")).isEmpty();
            assertThat(response.path("discarded")).isEmpty();
        }
        assertThat(readings.count()).isEqualTo(180);
        clock.now = start.plusSeconds(3600);
        scheduler.run();
        assertThat(readings.count()).isZero();
        FlowMeasurement average = flows.findAll().stream().filter(f -> "Sensor E2E".equals(f.getDeviceName())).findFirst().orElseThrow();
        assertThat(average.getSampleCount()).isEqualTo(60);
        assertThat(average.getHeightCm()).isEqualByComparingTo("15.00");
        assertThat(average.getFlowLiters()).isEqualByComparingTo("43.43");
        assertThat(average.getMeasuredAt()).isEqualTo(clock.instant());
        JsonNode list = request(get("/api/flow-measurements").header("Authorization", bearer(auth[2])));
        assertThat(list.path("content")).hasSize(1);
        assertThat(list.path("content").get(0).path("createdByName").asText()).isEqualTo("Equipamento: Sensor E2E");
        assertThat(request(get("/api/flow-measurements").header("Authorization", bearer(auth[3]))).path("content")).isEmpty();
        clock.now = clock.now.plusSeconds(1);
        JsonNode before = request(get("/api/dashboard").param("stationId", auth[0]).header("Authorization", bearer(auth[1])));
        clock.now = clock.now.plusSeconds(60);
        request(post("/api/device/readings").header("Authorization", device).contentType(MediaType.APPLICATION_JSON).content("{\"heightCm\":30}"));
        JsonNode after = request(get("/api/dashboard").param("stationId", auth[0]).header("Authorization", bearer(auth[1])));
        assertThat(after.path("currentDayAverages").path("flowTotalToday")).isEqualTo(before.path("currentDayAverages").path("flowTotalToday"));
        assertThat(after.path("series")).isEqualTo(before.path("series"));
        assertThat(after.path("latestFlow").path("measuredAt").asText()).isEqualTo(clock.instant().toString());
        assertThat(after.path("latestFlow").path("value")).isNotEqualTo(before.path("latestFlow").path("value"));
        for (String route : new String[]{"/api/flow-measurements", "/api/dashboard", "/api/device-keys"}) {
            mvc.perform(get(route).header("Authorization", device)).andExpect(status().isForbidden());
        }
        mvc.perform(patch("/api/device-keys/" + id + "/active").header("Authorization", bearer(auth[1]))
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isOk());
        mvc.perform(post("/api/device/readings").header("Authorization", device).contentType(MediaType.APPLICATION_JSON)
                .content("{\"heightCm\":10}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/device-keys/" + id).header("Authorization", bearer(auth[1]))).andExpect(status().isNoContent());
        assertThat(keys.findById(UUID.fromString(id))).isEmpty();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            FlowMeasurement retained = flows.findById(average.getId()).orElseThrow();
            assertThat(retained.getDeviceName()).isEqualTo("Sensor E2E");
            assertThat(retained.getDeviceKey()).isNull();
        });
    }

    private JsonNode request(MockHttpServletRequestBuilder request) throws Exception {
        return json.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private static String bearer(String token) { return "Bearer " + token; }
    static class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-11-13T17:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
    @TestConfiguration static class ClockConfig {
        @Bean @Primary MutableClock controlledClock() { return new MutableClock(); }
    }
}
