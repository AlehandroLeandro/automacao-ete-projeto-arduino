package br.com.ete.device;

import br.com.ete.common.error.GlobalExceptionHandler;
import br.com.ete.config.JwtProperties;
import br.com.ete.security.JwtService;
import br.com.ete.station.Station;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Clock;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DeviceAuthControllerTest {
    private static final String KEY = "Abcdefghijklmnop12345678";
    private final ObjectMapper mapper = new ObjectMapper();
    private DeviceKeyRepository repository;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repository = mock(DeviceKeyRepository.class);
        JwtService jwt = mock(JwtService.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        DeviceKeyHasher hasher = new DeviceKeyHasher();
        DeviceKey key = new DeviceKey(new Station("ETE", "ete"), "Sensor", "encrypted", hasher.hash(KEY), null);
        when(repository.findByValueHash(hasher.hash(KEY))).thenReturn(Optional.of(key));
        when(jwt.generateDeviceAccessToken(key)).thenReturn("device-jwt");
        DeviceAuthService service = new DeviceAuthService(repository, hasher, jwt,
                new DeviceLoginThrottle(Clock.systemUTC()), new JwtProperties("jwt-secret-which-is-at-least-32-characters", 20, 7), transactions);
        mvc = MockMvcBuilders.standaloneSetup(new DeviceAuthController(service, mapper))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void validKeyReturnsOnlyAccessTokenFields() throws Exception {
        String body = mvc.perform(post("/api/auth/device-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"" + KEY + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var json = mapper.readTree(body);
        assertThat(json.size()).isEqualTo(3);
        assertThat(json.get("accessToken").asText()).isEqualTo("device-jwt");
        assertThat(json.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(json.get("expiresInSeconds").asLong()).isEqualTo(1200);
    }

    @Test
    void failuresHaveSameGenericErrorAndBlockedIpDoesNotQueryDatabase() throws Exception {
        DeviceKey inactive = new DeviceKey(new Station("ETE", "ete"), "Sensor", "encrypted", "hash", null);
        inactive.deactivate();
        when(repository.findByValueHash(any())).thenReturn(Optional.empty(), Optional.of(inactive));
        String[] bodies = {"{\"key\":\"" + KEY + "\"}", "{\"key\":\"" + KEY + "\"}", "{}", "{\"key\":null}", "{\"key\":\"short\"}", "{\"key\":42}", "{", "{}", "{}", "{}"};
        com.fasterxml.jackson.databind.JsonNode expected = null;
        for (String body : bodies) {
            String response = mvc.perform(post("/api/auth/device-login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
            var json = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(response);
            json.remove("timestamp");
            if (expected == null) expected = json;
            else assertThat(json).isEqualTo(expected);
        }
        clearInvocations(repository);
        mvc.perform(post("/api/auth/device-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"" + KEY + "\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.message").doesNotExist());
        verifyNoInteractions(repository);
    }
}
