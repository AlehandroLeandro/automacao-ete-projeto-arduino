package br.com.carbonifera.ete.measurement;

import br.com.carbonifera.ete.security.UserPrincipal;
import br.com.carbonifera.ete.security.JwtService;
import br.com.carbonifera.ete.security.AppUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PhMeasurementController.class, properties = "server.port=0")
class PhMeasurementControllerTest {
    @Autowired MockMvc mockMvc;
    @MockBean PhMeasurementService service;
    @MockBean JwtService jwtService;
    @MockBean AppUserDetailsService userDetailsService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void returnsPageShape() throws Exception {
        when(service.list(any(), any(Boolean.class), any(), any(), any(), any(Boolean.class), any(), any(Integer.class), any(Integer.class), any()))
                .thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get("/api/ph-measurements?startAt=2026-01-01T00:00:00Z&endAt=2026-01-02T00:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.number").value(0));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void acceptsAuthorAndPaginationParameters() throws Exception {
        when(service.list(any(), any(Boolean.class), any(), any(), any(), any(Boolean.class), any(), any(Integer.class), any(Integer.class), any()))
                .thenReturn(new PageImpl<>(List.of()));
        mockMvc.perform(get("/api/ph-measurements?page=1&size=10&authorUserId=00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isOk());
    }
}
