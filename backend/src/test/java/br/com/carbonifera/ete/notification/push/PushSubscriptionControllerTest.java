package br.com.carbonifera.ete.notification.push;

import br.com.carbonifera.ete.config.CorsProperties;
import br.com.carbonifera.ete.config.PushProperties;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = PushSubscriptionController.class,
        properties = {
                "server.port=0"
        }
)
@Import({SecurityConfig.class, PushSubscriptionControllerTest.FilterTestConfig.class})
class PushSubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PushSubscriptionService service;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserDetailsService userDetailsService;

    @Test
    void subscriptionEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/push/subscriptions")
                        .contentType("application/json")
                        .content(subscriptionJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void publicKeyReturnsConfiguredVapidPublicKey() throws Exception {
        mockMvc.perform(get("/api/push/public-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicKey").value("configured-public-key"));
    }

    @Test
    @WithMockUser
    void subscribeDelegatesRequestToService() throws Exception {
        mockMvc.perform(post("/api/push/subscriptions")
                        .contentType("application/json")
                        .content(subscriptionJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endpoint").value("https://push.example/subscription"))
                .andExpect(jsonPath("$.active").value(true));

        verify(service).upsert(isNull(), eq(new PushSubscriptionRequest(
                "https://push.example/subscription",
                new PushSubscriptionRequest.Keys("p256dh-value", "auth-value")
        )));
    }

    @Test
    @WithMockUser
    void unsubscribeDelegatesEndpointToService() throws Exception {
        mockMvc.perform(delete("/api/push/subscriptions")
                        .contentType("application/json")
                        .content("{\"endpoint\":\"https://push.example/subscription\"}"))
                .andExpect(status().isNoContent());

        verify(service).deactivate(isNull(), eq("https://push.example/subscription"));
    }

    @Test
    @WithMockUser
    void subscribeRejectsOversizedEndpoint() throws Exception {
        String oversizedEndpoint = "https://push.example/" + "a".repeat(501);

        mockMvc.perform(post("/api/push/subscriptions")
                        .contentType("application/json")
                        .content("""
                                {
                                  "endpoint": "%s",
                                  "keys": {
                                    "p256dh": "p256dh-value",
                                    "auth": "auth-value"
                                  }
                                }
                                """.formatted(oversizedEndpoint)))
                .andExpect(status().isBadRequest());
    }

    private String subscriptionJson() {
        return """
                {
                  "endpoint": "https://push.example/subscription",
                  "keys": {
                    "p256dh": "p256dh-value",
                    "auth": "auth-value"
                  }
                }
                """;
    }

    @TestConfiguration
    static class FilterTestConfig {

        @Bean
        CorsProperties corsProperties() {
            return new CorsProperties("http://localhost:5174");
        }

        @Bean
        PushProperties pushProperties() {
            return new PushProperties("configured-public-key", "configured-private-key", "mailto:ete@example.com");
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
