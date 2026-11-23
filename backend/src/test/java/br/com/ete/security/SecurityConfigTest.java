package br.com.ete.security;

import br.com.ete.config.CorsProperties;
import br.com.ete.dashboard.DashboardController;
import br.com.ete.dashboard.DashboardService;
import br.com.ete.measurement.FlowMeasurementController;
import br.com.ete.measurement.FlowMeasurementService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(
        controllers = {FlowMeasurementController.class, DashboardController.class},
        properties = {
                "server.port=0"
        }
)
@Import({SecurityConfig.class, SecurityConfigTest.FilterTestConfig.class, SecurityConfigTest.DeviceEndpointProbe.class})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FlowMeasurementService flowMeasurementService;

    @MockBean
    private DashboardService dashboardService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserDetailsService userDetailsService;

    @Test
    void operationalWriteEndpointRequiresAuthentication() throws Exception {
        int status = mockMvc.perform(post("/api/flow-measurements")
                        .contentType("application/json")
                        .content("{}"))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status).isEqualTo(401);
    }

    @Test
    void operationalWriteEndpointReturnsUnauthorizedForExpiredOrInvalidToken() throws Exception {
        when(jwtService.isAccessToken("expired-token")).thenThrow(new JwtException("Expired JWT"));

        int status = mockMvc.perform(post("/api/flow-measurements")
                        .header("Authorization", "Bearer expired-token")
                        .contentType("application/json")
                        .content("{}"))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status).isEqualTo(401);
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void operationalWriteEndpointKeepsForbiddenForInsufficientRole() throws Exception {
        int status = mockMvc.perform(post("/api/flow-measurements")
                        .contentType("application/json")
                        .content("""
                                {
                                  "stationId": "a8efe295-8c1d-4d64-a198-a47a7e32dcf7",
                                  "heightCm": 10.00,
                                  "measuredAt": "2026-07-10T08:00:00Z"
                                }
                                """))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status).isEqualTo(403);
    }

    @Test
    void authEndpointsDoNotRequireAuthentication() throws Exception {
        assertThat(mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("{}"))
                .andReturn()
                .getResponse()
                .getStatus()).isNotIn(401, 403);

        assertThat(mockMvc.perform(post("/api/auth/refresh")
                        .contentType("application/json")
                        .content("{}"))
                .andReturn()
                .getResponse()
                .getStatus()).isNotIn(401, 403);
    }

    @Test
    void dashboardEndpointsRequireAuthentication() throws Exception {
        assertThat(mockMvc.perform(get("/api/dashboard"))
                .andReturn()
                .getResponse()
                .getStatus()).isEqualTo(401);

        assertThat(mockMvc.perform(get("/api/dashboard/activity"))
                .andReturn()
                .getResponse()
                .getStatus()).isEqualTo(401);

        assertThat(mockMvc.perform(get("/api/stations"))
                .andReturn()
                .getResponse()
                .getStatus()).isEqualTo(401);
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void authenticatedDashboardEndpointIsAllowed() throws Exception {
        int status = mockMvc.perform(get("/api/dashboard"))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status).isEqualTo(200);
    }

    @Test
    @WithMockUser(roles = "DEVICE")
    void deviceRoleIsForbiddenFromHumanEndpoints() throws Exception {
        for (String path : new String[]{
                "/api/flow-measurements", "/api/dashboard",
                "/api/notifications", "/api/device-keys"
        }) {
            assertThat(mockMvc.perform(get(path)).andReturn().getResponse().getStatus())
                    .as("GET %s", path)
                    .isEqualTo(403);
        }
        assertThat(mockMvc.perform(post("/api/auth/change-password")
                        .contentType("application/json").content("{}"))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @WithMockUser(roles = "DEVICE")
    void deviceRoleCanPostReadings() throws Exception {
        assertThat(mockMvc.perform(post("/api/device/readings"))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void deviceReadingsRequireAuthentication() throws Exception {
        assertThat(mockMvc.perform(post("/api/device/readings"))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OPERATOR", "SUPERVISOR", "ADMIN", "VIEWER"})
    void humanRolesAreForbiddenFromDeviceReadings(String role) throws Exception {
        var response = mockMvc.perform(post("/api/device/readings")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("test").roles(role))
                        .contentType("application/json")
                        .content("{}"))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void deviceLoginIsAccessibleWithoutToken() throws Exception {
        int status = mockMvc.perform(post("/api/auth/device-login")
                        .contentType("application/json")
                        .content("{}"))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status).isEqualTo(200);
    }

    // T31 verifies the filter chain before the device controllers are implemented.
    @RestController
    static class DeviceEndpointProbe {
        @PostMapping({"/api/auth/device-login", "/api/device/readings"})
        String accept() { return "ok"; }
    }

    @Test
    void preflightRequestAllowsConfiguredOrigin() throws Exception {
        var response = mockMvc.perform(options("/api/flow-measurements")
                        .header("Origin", "http://localhost:5174")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5174");
        assertThat(response.getHeader("Access-Control-Allow-Methods")).contains("POST");
        assertThat(response.getHeader("Access-Control-Allow-Headers")).contains("Authorization", "Content-Type");
    }

    @Test
    void preflightRequestRejectsUnconfiguredOrigin() throws Exception {
        var response = mockMvc.perform(options("/api/flow-measurements")
                        .header("Origin", "https://example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
    }

    @TestConfiguration
    static class FilterTestConfig {

        @Bean
        CorsProperties corsProperties() {
            return new CorsProperties("http://localhost:5174");
        }

        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter(
                JwtService jwtService,
                AppUserDetailsService userDetailsService
        ) {
            return new JwtAuthenticationFilter(jwtService, userDetailsService);
        }

        @Bean
        ChangePasswordGuardFilter changePasswordGuardFilter() {
            return new ChangePasswordGuardFilter() {
                @Override
                protected void doFilterInternal(
                        HttpServletRequest request,
                        HttpServletResponse response,
                        FilterChain filterChain
                ) throws ServletException, IOException {
                    filterChain.doFilter(request, response);
                }
            };
        }
    }
}
