package br.com.carbonifera.ete.station;

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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = StationController.class,
        properties = {
                "server.port=0"
        }
)
@Import({SecurityConfig.class, StationControllerTest.FilterTestConfig.class})
class StationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StationService stationService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserDetailsService userDetailsService;

    @Test
    @WithMockUser(roles = "OPERATOR")
    void updateLimeThresholdRejectsOperator() throws Exception {
        mockMvc.perform(put("/api/stations/{id}/lime-threshold", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{\"minLimeStockBags\": 10}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void updateLimeThresholdAllowsSupervisorAndReturnsPersistedMinimum() throws Exception {
        UUID stationId = UUID.randomUUID();
        when(stationService.updateLimeThreshold(stationId, new BigDecimal("12.5")))
                .thenReturn(new StationResponse(stationId, "ITE A", "ite-a", true, new BigDecimal("12.5")));

        mockMvc.perform(put("/api/stations/{id}/lime-threshold", stationId)
                        .contentType("application/json")
                        .content("{\"minLimeStockBags\": 12.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minLimeStockBags").value(12.5));

        verify(stationService).updateLimeThreshold(stationId, new BigDecimal("12.5"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateLimeThresholdAllowsAdminToRemoveMinimum() throws Exception {
        UUID stationId = UUID.randomUUID();
        when(stationService.updateLimeThreshold(stationId, null))
                .thenReturn(new StationResponse(stationId, "ITE A", "ite-a", true, null));

        mockMvc.perform(put("/api/stations/{id}/lime-threshold", stationId)
                        .contentType("application/json")
                        .content("{\"minLimeStockBags\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minLimeStockBags").doesNotExist());

        verify(stationService).updateLimeThreshold(stationId, null);
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
