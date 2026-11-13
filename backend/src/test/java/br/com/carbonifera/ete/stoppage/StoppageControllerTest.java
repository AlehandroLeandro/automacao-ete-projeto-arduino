package br.com.carbonifera.ete.stoppage;

import br.com.carbonifera.ete.common.error.BusinessException;
import br.com.carbonifera.ete.common.error.GlobalExceptionHandler;
import br.com.carbonifera.ete.security.UserPrincipal;
import br.com.carbonifera.ete.security.AppUserDetailsService;
import br.com.carbonifera.ete.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StoppageController.class, properties = "server.port=0")
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class StoppageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StoppageService service;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserDetailsService userDetailsService;

    @Test
    void startReturnsBadRequestWithBusinessMessage() throws Exception {
        when(service.start(any(), any()))
                .thenThrow(new BusinessException("Station already has an active stoppage"));

        mockMvc.perform(post("/api/stoppages/start")
                        .contentType("application/json")
                        .content("{\"stationId\":\"" + UUID.randomUUID() + "\",\"reason\":\"FALTA_DE_AGUA\",\"startAt\":\"2026-07-01T10:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Station already has an active stoppage"));
    }

    @Test
    void listReturnsPageWithCombinedFilters() throws Exception {
        UUID author = UUID.randomUUID();
        when(service.list(isNull(), eq(false), isNull(), isNull(), isNull(), eq(false), eq(author),
                eq(StoppageReason.FALTA_DE_AGUA), eq(1), eq(10), any())).thenReturn(
                        new PageImpl<>(List.of(), PageRequest.of(1, 10), 0));

        mockMvc.perform(get("/api/stoppages")
                        .param("authorUserId", author.toString())
                        .param("reason", "FALTA_DE_AGUA")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.number").value(1));
        verify(service).list(isNull(), eq(false), isNull(), isNull(), isNull(), eq(false), eq(author),
                eq(StoppageReason.FALTA_DE_AGUA), eq(1), eq(10), any());
    }
}
