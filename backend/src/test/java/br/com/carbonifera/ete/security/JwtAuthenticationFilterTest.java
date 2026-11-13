package br.com.carbonifera.ete.security;

import br.com.carbonifera.ete.config.JwtProperties;
import br.com.carbonifera.ete.device.DeviceKey;
import br.com.carbonifera.ete.device.DeviceKeyRepository;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "jwt_secret_with_at_least_32_characters";
    private JwtService jwtService;
    private AppUserDetailsService userDetailsService;
    private DeviceKeyRepository deviceKeyRepository;
    private JwtAuthenticationFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(SECRET, 15, 30));
        userDetailsService = mock(AppUserDetailsService.class);
        deviceKeyRepository = mock(DeviceKeyRepository.class);
        filter = new JwtAuthenticationFilter(jwtService, userDetailsService, deviceKeyRepository);
        chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesActiveDeviceWithDeviceAuthority() throws Exception {
        DeviceKey key = deviceKey();
        when(deviceKeyRepository.findById(key.getId())).thenReturn(Optional.of(key));

        doFilter(jwtService.generateDeviceAccessToken(key));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication.getPrincipal()).isInstanceOf(DevicePrincipal.class);
        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_DEVICE");
        assertThat(((DevicePrincipal) authentication.getPrincipal()).getDeviceKeyId()).isEqualTo(key.getId());
    }

    @Test
    void inactiveDeviceKeyLeavesRequestUnauthenticated() throws Exception {
        DeviceKey inactive = deviceKey();
        inactive.deactivate();
        when(deviceKeyRepository.findById(inactive.getId())).thenReturn(Optional.of(inactive));
        doFilter(jwtService.generateDeviceAccessToken(inactive));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingDeviceKeyLeavesRequestUnauthenticated() throws Exception {
        DeviceKey missing = deviceKey();
        when(deviceKeyRepository.findById(missing.getId())).thenReturn(Optional.empty());
        doFilter(jwtService.generateDeviceAccessToken(missing));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void continuesAuthenticatingUserAccessTokens() throws Exception {
        AppUser user = new AppUser("Admin", "admin", "hash", UserRole.ADMIN, false);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        UserDetails principal = mock(UserDetails.class);
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(principal);

        doFilter(jwtService.generateAccessToken(user));

        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(principal);
        verify(userDetailsService).loadUserByUsername("admin");
    }

    @Test
    void ignoresTamperedTokensAndContinuesFilterChain() throws Exception {
        String valid = jwtService.generateDeviceAccessToken(deviceKey());
        String tampered = valid.substring(0, valid.length() - 2) + "xx";

        doFilter(tampered);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private DeviceKey deviceKey() {
        Station station = new Station("Station", "station");
        ReflectionTestUtils.setField(station, "id", UUID.randomUUID());
        AppUser admin = new AppUser("Admin", "admin", "hash", UserRole.ADMIN, false);
        DeviceKey key = new DeviceKey(station, "Arduino", "encrypted", "hash", admin);
        ReflectionTestUtils.setField(key, "id", UUID.randomUUID());
        return key;
    }

    private void doFilter(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilterInternal(request, new MockHttpServletResponse(), chain);
    }
}
