package br.com.carbonifera.ete.user;

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
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserDirectoryController.class, properties = "server.port=0")
@Import({SecurityConfig.class, UserDirectoryControllerTest.FilterTestConfig.class})
class UserDirectoryControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean UserService userService;
    @MockBean JwtService jwtService;
    @MockBean AppUserDetailsService userDetailsService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminGetsDirectoryShape() throws Exception {
        when(userService.listDirectory()).thenReturn(List.of(new UserDirectoryEntry(UUID.randomUUID(), "Admin", UserRole.ADMIN)));
        mockMvc.perform(get("/api/users/directory")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Admin"))
                .andExpect(jsonPath("$[0].username").doesNotExist())
                .andExpect(jsonPath("$[0].active").doesNotExist())
                .andExpect(jsonPath("$[0].changePassword").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "SUPERVISOR")
    void supervisorGetsDirectory() throws Exception {
        when(userService.listDirectory()).thenReturn(List.of());
        mockMvc.perform(get("/api/users/directory")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void operatorIsForbidden() throws Exception {
        mockMvc.perform(get("/api/users/directory")).andExpect(status().isForbidden());
    }

    @TestConfiguration
    static class FilterTestConfig {
        @Bean CorsProperties corsProperties() { return new CorsProperties("http://localhost:5174"); }
        @Bean JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService, AppUserDetailsService details) {
            return new JwtAuthenticationFilter(jwtService, details);
        }
        @Bean ChangePasswordGuardFilter changePasswordGuardFilter() {
            return new ChangePasswordGuardFilter() {
                @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException { chain.doFilter(request, response); }
            };
        }
    }
}
