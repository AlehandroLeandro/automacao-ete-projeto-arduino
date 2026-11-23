package br.com.ete.device;

import br.com.ete.common.error.BusinessException;
import br.com.ete.config.CorsProperties;
import br.com.ete.security.AppUserDetailsService;
import br.com.ete.security.ChangePasswordGuardFilter;
import br.com.ete.security.JwtAuthenticationFilter;
import br.com.ete.security.JwtService;
import br.com.ete.security.SecurityConfig;
import br.com.ete.security.UserPrincipal;
import br.com.ete.user.AppUser;
import br.com.ete.user.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    void adminListsKeysWithoutSecret() throws Exception {
        authenticate(UserRole.ADMIN);
        when(service.list()).thenReturn(List.of(KEY));
        mockMvc.perform(get("/api/device-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ID.toString()))
                .andExpect(jsonPath("$[0].name").value("Sensor"))
                .andExpect(jsonPath("$[0].value").doesNotExist())
                .andExpect(jsonPath("$[0].stationId").value(ID.toString()))
                .andExpect(jsonPath("$[0].stationName").value("ETE Norte"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].createdAt").value("2026-01-01T00:00:00Z"));
        verify(service).list();
    }

    @Test
    void adminCreatesKeyWithoutSecretInResponse() throws Exception {
        UserPrincipal admin = authenticate(UserRole.ADMIN);
        when(service.create(eq("Sensor"), eq(ID), same(admin))).thenReturn(new DeviceKeyCreatedResponse(KEY, "secret-value"));
        mockMvc.perform(post("/api/device-keys").contentType("application/json")
                        .content("{\"name\":\"Sensor\",\"stationId\":\"" + ID + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.value").doesNotExist())
                .andExpect(jsonPath("$.key").doesNotExist())
                .andExpect(jsonPath("$.name").value("Sensor"))
                .andExpect(jsonPath("$.stationId").value(ID.toString()))
                .andExpect(jsonPath("$.active").value(true));
        verify(service).create(eq("Sensor"), eq(ID), same(admin));
    }

    @Test
    void adminGetsSecretOnDemand() throws Exception {
        authenticate(UserRole.ADMIN);
        when(service.secret(ID)).thenReturn(new DeviceKeySecretResponse("secret-value"));
        mockMvc.perform(get("/api/device-keys/{id}/secret", ID))
                .andExpect(status().isOk()).andExpect(jsonPath("$.value").value("secret-value"));
        verify(service).secret(ID);
    }

    @Test
    void adminChangesActiveState() throws Exception {
        UserPrincipal admin = authenticate(UserRole.ADMIN);
        DeviceKeyResponse inactiveKey = new DeviceKeyResponse(ID, "Sensor", ID, "ETE Norte",
                false, KEY.createdAt(), null, DeviceKeyStatus.NEVER);
        when(service.setActive(eq(ID), eq(false), same(admin))).thenReturn(inactiveKey);
        mockMvc.perform(patch("/api/device-keys/{id}/active", ID).contentType("application/json")
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ID.toString()))
                .andExpect(jsonPath("$.active").value(false));
        verify(service).setActive(eq(ID), eq(false), same(admin));
    }

    @Test
    void adminDeletesKeyWithNoContent() throws Exception {
        UserPrincipal admin = authenticate(UserRole.ADMIN);
        mockMvc.perform(delete("/api/device-keys/{id}", ID)).andExpect(status().isNoContent());
        verify(service).delete(eq(ID), same(admin));
    }

    @Test
    void rejectsBlankName() throws Exception {
        authenticate(UserRole.ADMIN);
        mockMvc.perform(post("/api/device-keys").contentType("application/json")
                        .content("{\"name\":\"  \",\"stationId\":\"" + ID + "\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingStation() throws Exception {
        authenticate(UserRole.ADMIN);
        mockMvc.perform(post("/api/device-keys").contentType("application/json")
                        .content("{\"name\":\"Sensor\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void duplicateNameReturnsConflict() throws Exception {
        UserPrincipal admin = authenticate(UserRole.ADMIN);
        when(service.create(eq("Sensor"), eq(ID), same(admin)))
                .thenThrow(new BusinessException("Device key name already exists", HttpStatus.CONFLICT));
        mockMvc.perform(post("/api/device-keys").contentType("application/json")
                        .content("{\"name\":\"Sensor\",\"stationId\":\"" + ID + "\"}"))
                .andExpect(status().isConflict());
        verify(service).create(eq("Sensor"), eq(ID), same(admin));
    }

    @Test
    void operatorCannotUseAnyDeviceKeyRoute() throws Exception {
        authenticate(UserRole.OPERATOR);
        assertAllRoutesForbidden();
    }

    @Test
    void supervisorCannotUseAnyDeviceKeyRoute() throws Exception {
        authenticate(UserRole.SUPERVISOR);
        assertAllRoutesForbidden();
    }

    @Test
    void viewerCannotUseAnyDeviceKeyRoute() throws Exception {
        authenticate(UserRole.VIEWER);
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
        verifyNoInteractions(service);
    }

    private UserPrincipal authenticate(UserRole role) {
        UserPrincipal principal = UserPrincipal.from(new AppUser("Test user", "test-user", "hash", role, false));
        TestSecurityContextHolder.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        return principal;
    }

    @AfterEach
    void clearSecurityContext() {
        TestSecurityContextHolder.clearContext();
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
