package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.common.error.BusinessException;
import br.com.carbonifera.ete.config.CorsProperties;
import br.com.carbonifera.ete.security.AppUserDetailsService;
import br.com.carbonifera.ete.security.ChangePasswordGuardFilter;
import br.com.carbonifera.ete.security.JwtAuthenticationFilter;
import br.com.carbonifera.ete.security.JwtService;
import br.com.carbonifera.ete.security.SecurityConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DeviceKeyController.class, properties = "server.port=0")
@Import({SecurityConfig.class, DeviceKeyControllerTest.FilterTestConfig.class})
class DeviceKeyControllerTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final DeviceKeyResponse KEY = new DeviceKeyResponse(ID, "Sensor", ID, "ETE Norte",
            true, Instant.parse("2026-01-01T00:00:00Z"), null, DeviceKeyStatus.NEVER);

    @Autowired MockMvc mockMvc;
    @MockBean DeviceKeyService service;
    @MockBean JwtService jwtService;
    @MockBean AppUserDetailsService userDetailsService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminListsKeysWithoutSecret() throws Exception {
        when(service.list()).thenReturn(List.of(KEY));
        mockMvc.perform(get("/api/device-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ID.toString()))
                .andExpect(jsonPath("$[0].name").value("Sensor"))
                .andExpect(jsonPath("$[0].value").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCreatesKeyWithoutSecretInResponse() throws Exception {
        when(service.create(any(), any(), any())).thenReturn(new DeviceKeyCreatedResponse(KEY, "secret-value"));
        mockMvc.perform(post("/api/device-keys").contentType("application/json")
                        .content("{\"name\":\"Sensor\",\"stationId\":\"" + ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.value").doesNotExist())
                .andExpect(jsonPath("$.key").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminGetsSecretOnDemand() throws Exception {
        when(service.secret(ID)).thenReturn(new DeviceKeySecretResponse("secret-value"));
        mockMvc.perform(get("/api/device-keys/{id}/secret", ID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.value").value("secret-value"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminChangesActiveState() throws Exception {
        when(service.setActive(any(), anyBoolean(), any())).thenReturn(KEY);
        mockMvc.perform(patch("/api/device-keys/{id}/active", ID).contentType("application/json")
                        .content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminDeletesKeyWithNoContent() throws Exception {
        mockMvc.perform(delete("/api/device-keys/{id}", ID)).andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejectsBlankName() throws Exception {
        mockMvc.perform(post("/api/device-keys").contentType("application/json")
                        .content("{\"name\":\"  \",\"stationId\":\"" + ID + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void rejectsMissingStation() throws Exception {
        mockMvc.perform(post("/api/device-keys").contentType("application/json")
                        .content("{\"name\":\"Sensor\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void duplicateNameReturnsConflict() throws Exception {
        when(service.create(any(), any(), any()))
                .thenThrow(new BusinessException("Device key name already exists", HttpStatus.CONFLICT));
        mockMvc.perform(post("/api/device-keys").contentType("application/json")
                        .content("{\"name\":\"Sensor\",\"stationId\":\"" + ID + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorCannotUseAnyDeviceKeyRoute() throws Exception {
        assertAllRoutesForbidden();
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void supervisorCannotUseAnyDeviceKeyRoute() throws Exception {
        assertAllRoutesForbidden();
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    void viewerCannotUseAnyDeviceKeyRoute() throws Exception {
        assertAllRoutesForbidden();
    }

    @Test
    @WithMockUser
    void unauthenticatedRoleCannotUseAnyDeviceKeyRoute() throws Exception {
        assertAllRoutesForbidden();
    }

    private void assertAllRoutesForbidden() throws Exception {
        List<MockHttpServletRequestBuilder> requests = List.of(
                get("/api/device-keys"),
                post("/api/device-keys").contentType("application/json")
                        .content("{\"name\":\"Sensor\",\"stationId\":\"" + ID + "\"}"),
                get("/api/device-keys/{id}/secret", ID),
                patch("/api/device-keys/{id}/active", ID).contentType("application/json")
                        .content("{\"active\":false}"),
                delete("/api/device-keys/{id}", ID)
        );
        for (MockHttpServletRequestBuilder request : requests) {
            mockMvc.perform(request).andExpect(status().isForbidden());
        }
    }

    @TestConfiguration
    static class FilterTestConfig {
        @Bean
        CorsProperties corsProperties() {
            return new CorsProperties("http://localhost:5174");
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService,
                                                         AppUserDetailsService userDetailsService) {
            return new JwtAuthenticationFilter(jwtService, userDetailsService);
        }

        @Bean
        ChangePasswordGuardFilter changePasswordGuardFilter() {
            return new ChangePasswordGuardFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                FilterChain filterChain) throws ServletException, IOException {
                    filterChain.doFilter(request, response);
                }
            };
        }
    }
}
