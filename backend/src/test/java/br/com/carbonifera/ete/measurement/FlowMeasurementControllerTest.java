package br.com.carbonifera.ete.measurement;

import br.com.carbonifera.ete.common.error.GlobalExceptionHandler;
import br.com.carbonifera.ete.security.AppUserDetailsService;
import br.com.carbonifera.ete.security.JwtService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FlowMeasurementController.class, properties = "server.port=0")
@Import(GlobalExceptionHandler.class)
class FlowMeasurementControllerTest {

    private static final Instant MEASURED_AT = Instant.parse("2026-07-01T10:00:00Z");

    @Autowired
    MockMvc mockMvc;

    @MockBean
    FlowMeasurementService service;

    @MockBean
    JwtService jwtService;

    @MockBean
    AppUserDetailsService userDetailsService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void returnsPagedResponse() throws Exception {
        when(service.list(any(), any(Boolean.class), any(), any(), any(Boolean.class), any(), any(Integer.class), any(Integer.class), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/flow-measurements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void acceptsPagination() throws Exception {
        when(service.list(any(), any(Boolean.class), any(), any(), any(Boolean.class), any(), any(Integer.class), any(Integer.class), any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/flow-measurements?page=1&size=10")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createRejectsMissingHeight() throws Exception {
        mockMvc.perform(post("/api/flow-measurements")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].field").value("heightCm"));

        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createRejectsHeightAboveMaximum() throws Exception {
        assertInvalidCreateHeight("45.01");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createRejectsNegativeHeight() throws Exception {
        assertInvalidCreateHeight("-0.01");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createRejectsHeightWithMoreThanTwoDecimalPlaces() throws Exception {
        assertInvalidCreateHeight("1.234");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createAcceptsInclusiveHeightBoundaries() throws Exception {
        when(service.create(any(), any())).thenReturn(response(BigDecimal.ZERO));

        mockMvc.perform(post("/api/flow-measurements")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("\"heightCm\":0,")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/flow-measurements")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("\"heightCm\":45,")))
                .andExpect(status().isOk());

        ArgumentCaptor<FlowMeasurementRequest> requestCaptor = ArgumentCaptor.forClass(FlowMeasurementRequest.class);
        verify(service, times(2)).create(requestCaptor.capture(), any());
        assertThat(requestCaptor.getAllValues())
                .extracting(FlowMeasurementRequest::heightCm)
                .containsExactly(BigDecimal.ZERO, new BigDecimal("45"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createIgnoresClientProvidedFlowLiters() throws Exception {
        when(service.create(any(), any())).thenReturn(response(new BigDecimal("19.76")));

        mockMvc.perform(post("/api/flow-measurements")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("\"heightCm\":10,\"flowLiters\":999999,")))
                .andExpect(status().isOk());

        ArgumentCaptor<FlowMeasurementRequest> requestCaptor = ArgumentCaptor.forClass(FlowMeasurementRequest.class);
        verify(service).create(requestCaptor.capture(), any());
        assertThat(requestCaptor.getValue().heightCm()).isEqualByComparingTo("10");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRejectsNegativeHeight() throws Exception {
        mockMvc.perform(put("/api/flow-measurements/{measurementId}", UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stationId": "%s",
                                  "heightCm": -1,
                                  "measuredAt": "%s",
                                  "description": "Correcao da vazao"
                                }
                                """.formatted(UUID.randomUUID(), MEASURED_AT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].field").value("heightCm"));

        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRejectsHeightAboveMaximum() throws Exception {
        mockMvc.perform(put("/api/flow-measurements/{measurementId}", UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stationId": "%s",
                                  "heightCm": 45.01,
                                  "measuredAt": "%s",
                                  "description": "Correcao da vazao"
                                }
                                """.formatted(UUID.randomUUID(), MEASURED_AT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].field").value("heightCm"));

        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateAcceptsValidHeight() throws Exception {
        when(service.update(any(), any(), any())).thenReturn(response(new BigDecimal("19.76")));

        mockMvc.perform(put("/api/flow-measurements/{measurementId}", UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stationId": "%s",
                                  "heightCm": 10,
                                  "measuredAt": "%s",
                                  "description": "Correcao da vazao"
                                }
                                """.formatted(UUID.randomUUID(), MEASURED_AT)))
                .andExpect(status().isOk());

        ArgumentCaptor<FlowMeasurementUpdateRequest> requestCaptor = ArgumentCaptor.forClass(FlowMeasurementUpdateRequest.class);
        verify(service).update(any(), requestCaptor.capture(), any());
        assertThat(requestCaptor.getValue().heightCm()).isEqualByComparingTo("10");
    }

    private void assertInvalidCreateHeight(String heightCm) throws Exception {
        mockMvc.perform(post("/api/flow-measurements")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("\"heightCm\":" + heightCm + ",")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields[0].field").value("heightCm"));

        verifyNoInteractions(service);
    }

    private String createJson(String extraFields) {
        return """
                {
                  "stationId": "%s",
                  %s
                  "measuredAt": "%s"
                }
                """.formatted(UUID.randomUUID(), extraFields, MEASURED_AT);
    }

    private FlowMeasurementResponse response(BigDecimal flowLiters) {
        return new FlowMeasurementResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ITE A",
                flowLiters,
                MEASURED_AT,
                UUID.randomUUID(),
                "Admin"
        );
    }
}
