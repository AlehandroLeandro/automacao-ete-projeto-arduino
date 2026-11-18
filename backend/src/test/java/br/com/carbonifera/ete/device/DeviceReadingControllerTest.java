package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.config.CorsProperties;
import br.com.carbonifera.ete.security.*;
import br.com.carbonifera.ete.station.Station;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = DeviceReadingController.class, properties = "server.port=0")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, ChangePasswordGuardFilter.class, DeviceReadingControllerTest.Config.class})
class DeviceReadingControllerTest {
    @Autowired MockMvc mvc;
    @MockBean DeviceReadingService service;
    @MockBean DeviceKeyRepository keys;
    @MockBean JwtService jwt;
    @MockBean AppUserDetailsService users;
    @TestConfiguration static class Config {
        @Bean CorsProperties corsProperties() { return new CorsProperties("http://localhost:5174"); }
    }
    @Test void deviceTokenReceivesActualResponse() throws Exception {
        UUID id = UUID.randomUUID(), stationId = UUID.randomUUID();
        Station station = new Station("ETE", "ete");
        ReflectionTestUtils.setField(station, "id", stationId);
        DeviceKey key = new DeviceKey(station, "sensor", "encrypted", "hash", null);
        ReflectionTestUtils.setField(key, "id", id);
        when(jwt.isDeviceToken("valid")).thenReturn(true);
        when(jwt.deviceKeyId("valid")).thenReturn(id);
        when(keys.findById(id)).thenReturn(Optional.of(key));
        when(service.receive(eq(id), any())).thenReturn(new DeviceReadingResponse(List.of("rawPh"),
                List.of(new DeviceReadingResponse.FieldResult("heightCm", "invalid")),
                List.of(new DeviceReadingResponse.FieldResult("treatedPh", "EDITED"))));
        mvc.perform(post("/api/device/readings").header("Authorization", "Bearer valid")
                        .contentType("application/json").content("{\"rawPh\":7,\"heightCm\":46}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accepted[0]").value("rawPh"))
                .andExpect(jsonPath("$.rejected[0].field").value("heightCm"))
                .andExpect(jsonPath("$.discarded[0].field").value("treatedPh"))
                .andExpect(jsonPath("$.discarded[0].reason").value("EDITED"));
        verify(service).receive(eq(id), any());
    }
    @ParameterizedTest @ValueSource(strings = {"OPERATOR", "SUPERVISOR", "ADMIN", "VIEWER"})
    void humanRolesAreForbidden(String role) throws Exception {
        mvc.perform(post("/api/device/readings").with(user("human").roles(role))
                .contentType("application/json").content("{\"rawPh\":7}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void invalidOrInactiveTokenIsUnauthorized() throws Exception {
        when(jwt.isDeviceToken("inactive")).thenReturn(true);
        UUID id = UUID.randomUUID();
        when(jwt.deviceKeyId("inactive")).thenReturn(id);
        DeviceKey key = new DeviceKey(new Station("ETE", "ete"), "sensor", "encrypted", "hash", null);
        key.deactivate();
        when(keys.findById(id)).thenReturn(Optional.of(key));
        for (String token : List.of("inactive", "invalid")) {
            mvc.perform(post("/api/device/readings").header("Authorization", "Bearer " + token)
                    .contentType("application/json").content("{\"rawPh\":7}"))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(service);
    }
}
