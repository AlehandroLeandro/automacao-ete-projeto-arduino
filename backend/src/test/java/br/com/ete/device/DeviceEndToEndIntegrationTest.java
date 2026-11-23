package br.com.ete.device;

import br.com.ete.measurement.FlowMeasurement;
import br.com.ete.measurement.FlowMeasurementRepository;
import br.com.ete.security.JwtService;
import br.com.ete.station.Station;
import br.com.ete.user.AppUser;
import br.com.ete.user.UserRole;
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
        clock.now = clock.now.plusSeconds(60);
        request(post("/api/device/readings").header("Authorization", device).contentType(MediaType.APPLICATION_JSON).content("{\"heightCm\":40}"));
        JsonNode after = request(get("/api/dashboard").param("stationId", auth[0]).header("Authorization", bearer(auth[1])));
        assertThat(after.path("currentDayAverages").path("flowTotalToday")).isEqualTo(before.path("currentDayAverages").path("flowTotalToday"));
        assertThat(after.path("series")).isEqualTo(before.path("series"));
        assertThat(after.path("latestFlow").path("measuredAt").asText()).isEqualTo(clock.instant().toString());
        assertThat(after.path("latestFlow").path("value").decimalValue()).isEqualByComparingTo("237.93");
        String batch = "{\"readings\":[{\"id\":\"sample-1\",\"heightCm\":10,\"rawPh\":20,\"timeEstimated\":true}]}";
        long readingsBeforeBatch = readings.count();
        JsonNode firstBatch = request(post("/api/device/readings/batch").header("Authorization", device)
                .contentType(MediaType.APPLICATION_JSON).content(batch));
        assertThat(firstBatch.path("results").get(0).path("accepted").get(0).asText()).isEqualTo("heightCm");
        assertThat(firstBatch.path("results").get(0).path("rejected")).hasSize(1);
        JsonNode estimatedDashboard = request(get("/api/dashboard").param("stationId", auth[0]).header("Authorization", bearer(auth[1])));
        assertThat(estimatedDashboard.path("latestFlow").path("timeEstimated").asBoolean()).isTrue();
        assertThat(estimatedDashboard.path("latestFlow").path("timeEstimateOrigin").asText()).isEqualTo("RECEIVED_AT");
        long readingsAfterFirstBatch = readings.count();
        assertThat(readingsAfterFirstBatch).isEqualTo(readingsBeforeBatch + 1);
        JsonNode repeated = request(post("/api/device/readings/batch").header("Authorization", device)
                .contentType(MediaType.APPLICATION_JSON).content(batch));
        assertThat(repeated.path("results").get(0).path("duplicate").asBoolean()).isTrue();
        assertThat(repeated.path("results").get(0).path("accepted")).isEqualTo(firstBatch.path("results").get(0).path("accepted"));
        assertThat(readings.count()).isEqualTo(readingsAfterFirstBatch);
        mvc.perform(post("/api/device/readings/batch").header("Authorization", device).contentType(MediaType.APPLICATION_JSON)
                .content("{\"readings\":[{\"id\":\"a\",\"timeEstimated\":true},{\"id\":\"a\",\"timeEstimated\":true}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/device/readings/batch").header("Authorization", bearer(auth[1]))
                .contentType(MediaType.APPLICATION_JSON).content(batch)).andExpect(status().isForbidden());
        mvc.perform(post("/api/device/readings/batch").contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isUnauthorized());
        for (int i = 0; i < 12; i++) {
            assertThat(request(post("/api/device/readings/batch").header("Authorization", device)
                    .contentType(MediaType.APPLICATION_JSON).content(batch)).path("results").get(0).path("duplicate").asBoolean()).isTrue();
        }
        var hundred = java.util.stream.IntStream.range(0, 100)
                .mapToObj(i -> Map.of("id", "bulk-" + i, "rawPh", 20, "timeEstimated", true)).toList();
        assertThat(request(post("/api/device/readings/batch").header("Authorization", device)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("readings", hundred))))
                .path("results")).hasSize(100);
        var tooMany = new java.util.ArrayList<>(hundred);
        tooMany.add(Map.of("id", "extra", "rawPh", 20, "timeEstimated", true));
        for (String bad : new String[]{"{", "{\"readings\":[]}",
                "{\"readings\":[{\"id\":\"bad id\",\"timeEstimated\":true}]}",
                "{\"readings\":[{\"id\":\"no-time\",\"timeEstimated\":false}]}",
                json.writeValueAsString(Map.of("readings", java.util.List.of(Map.of("id", "future", "timeEstimated", false, "measuredAt", clock.instant().plusSeconds(1).toString())))),
                json.writeValueAsString(Map.of("readings", tooMany))}) {
            mvc.perform(post("/api/device/readings/batch").header("Authorization", device)
                    .contentType(MediaType.APPLICATION_JSON).content(bad)).andExpect(status().isBadRequest());
        }
        Instant boundary = clock.instant().minusSeconds(48 * 3600L);
        String windowBatch = json.writeValueAsString(Map.of("readings", java.util.List.of(
                Map.of("id", "at-boundary", "rawPh", 6, "measuredAt", boundary.toString(), "timeEstimated", true),
                Map.of("id", "too-old", "rawPh", 6, "measuredAt", boundary.minusSeconds(1).toString(), "timeEstimated", false),
                Map.of("id", "elapsed", "heightCm", 12, "measuredAt", clock.instant().toString(), "timeEstimated", true))));
        JsonNode windowResult = request(post("/api/device/readings/batch").header("Authorization", device)
                .contentType(MediaType.APPLICATION_JSON).content(windowBatch));
        assertThat(windowResult.path("results").get(0).path("accepted").get(0).asText()).isEqualTo("rawPh");
        assertThat(windowResult.path("results").get(1).path("discarded").get(0).path("reason").asText()).isEqualTo("TOO_OLD");
        JsonNode secondKey = request(post("/api/device-keys").header("Authorization", bearer(auth[1]))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("name", "Second sensor", "stationId", auth[0]))));
        String secondSecret = request(get("/api/device-keys/" + secondKey.path("id").asText() + "/secret")
                .header("Authorization", bearer(auth[1]))).path("value").asText();
        String secondDevice = bearer(request(post("/api/auth/device-login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("key", secondSecret)))).path("accessToken").asText());
        assertThat(request(post("/api/device/readings/batch").header("Authorization", secondDevice)
                .contentType(MediaType.APPLICATION_JSON).content("{\"readings\":[{\"id\":\"sample-1\",\"rawPh\":20,\"timeEstimated\":true}]}")).path("results").get(0).path("duplicate").asBoolean()).isFalse();
        request(put("/api/flow-measurements/" + average.getId()).header("Authorization", bearer(auth[1]))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                        "stationId", auth[0], "heightCm", 16, "measuredAt", average.getMeasuredAt().toString(),
                        "description", "Correcao manual autorizada"))));
        String conflict = json.writeValueAsString(Map.of("readings", java.util.List.of(Map.of(
                "id", "edited-window", "heightCm", 12, "measuredAt", start.plusSeconds(1800).toString(), "timeEstimated", false))));
        assertThat(request(post("/api/device/readings/batch").header("Authorization", device)
                .contentType(MediaType.APPLICATION_JSON).content(conflict)).path("results").get(0)
                .path("discarded").get(0).path("reason").asText()).isEqualTo("EDITED");
        clock.now = start.plusSeconds(7200);
        scheduler.run();
        FlowMeasurement mixed = flows.findAll().stream().filter(f -> "Sensor E2E".equals(f.getDeviceName()))
                .filter(f -> f.getDeviceWindowEnd().equals(start.plusSeconds(7200))).findFirst().orElseThrow();
        assertThat(mixed.getTimeEstimateOrigin()).isEqualTo("MIXED");
        JsonNode mixedDetail = request(get("/api/flow-measurements/" + mixed.getId()).header("Authorization", bearer(auth[1])));
        assertThat(mixedDetail.path("measurement").path("timeEstimated").asBoolean()).isTrue();
        assertThat(mixedDetail.path("measurement").path("timeEstimateOrigin").asText()).isEqualTo("MIXED");
        clock.now = clock.now.plusSeconds(1);
        JsonNode mixedDashboard = request(get("/api/dashboard").param("stationId", auth[0]).header("Authorization", bearer(auth[1])));
        assertThat(mixedDashboard.path("currentDayAverages").path("flow").path("timeEstimateOrigin").asText()).isEqualTo("MIXED");
        mvc.perform(delete("/api/flow-measurements/" + mixed.getId()).header("Authorization", bearer(auth[1]))
                .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"Exclusao manual autorizada\"}"))
                .andExpect(status().isNoContent());
        String deletedConflict = json.writeValueAsString(Map.of("readings", java.util.List.of(Map.of(
                "id", "deleted-window", "heightCm", 12, "measuredAt", start.plusSeconds(3660).toString(), "timeEstimated", false))));
        assertThat(request(post("/api/device/readings/batch").header("Authorization", device)
                .contentType(MediaType.APPLICATION_JSON).content(deletedConflict)).path("results").get(0)
                .path("discarded").get(0).path("reason").asText()).isEqualTo("DELETED");
        for (String route : new String[]{"/api/flow-measurements", "/api/dashboard", "/api/device-keys"}) {
            mvc.perform(get(route).header("Authorization", device)).andExpect(status().isForbidden());
        }
        mvc.perform(patch("/api/device-keys/" + id + "/active").header("Authorization", bearer(auth[1]))
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isOk());
        mvc.perform(post("/api/device/readings").header("Authorization", device).contentType(MediaType.APPLICATION_JSON)
                .content("{\"heightCm\":10}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/device-keys/" + id).header("Authorization", bearer(auth[1]))).andExpect(status().isNoContent());
        assertThat(keys.findById(UUID.fromString(id))).isEmpty();
        FlowMeasurement latestStored = flows.findAll().stream()
                .filter(f -> "Sensor E2E".equals(f.getDeviceName()) && !f.isDeleted())
                .max(java.util.Comparator.comparing(FlowMeasurement::getMeasuredAt)).orElseThrow();
        JsonNode afterDeletion = request(get("/api/dashboard").param("stationId", auth[0]).header("Authorization", bearer(auth[1])));
        assertThat(afterDeletion.path("latestFlow").path("value").decimalValue()).isEqualByComparingTo(latestStored.getFlowLiters());
        assertThat(afterDeletion.path("latestFlow").path("measuredAt").asText()).isEqualTo(latestStored.getMeasuredAt().toString());
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
