package br.com.ete.security;

import br.com.ete.device.DeviceKey;
import br.com.ete.device.DeviceKeyRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;
    private final DeviceKeyRepository deviceKeyRepository;

    @Autowired
    public JwtAuthenticationFilter(JwtService jwtService, AppUserDetailsService userDetailsService,
                                   DeviceKeyRepository deviceKeyRepository) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.deviceKeyRepository = deviceKeyRepository;
    }

    public JwtAuthenticationFilter(JwtService jwtService, AppUserDetailsService userDetailsService) {
        this(jwtService, userDetailsService, null);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(request, token);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        try {
            if (jwtService.isDeviceToken(token)) {
                if (deviceKeyRepository == null) {
                    return;
                }
                DeviceKey deviceKey = deviceKeyRepository.findById(jwtService.deviceKeyId(token)).orElse(null);
                if (deviceKey == null || !deviceKey.isActive()) {
                    return;
                }
                UserDetails devicePrincipal = new DevicePrincipal(deviceKey.getId(),
                        deviceKey.getStation().getId(), deviceKey.getName());
                setAuthentication(request, devicePrincipal);
                return;
            }
            if (!jwtService.isAccessToken(token)) {
                return;
            }
            String username = jwtService.subject(token);
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            setAuthentication(request, userDetails);
        } catch (JwtException | IllegalArgumentException ignored) {
            SecurityContextHolder.clearContext();
        }
    }

    private void setAuthentication(HttpServletRequest request, UserDetails principal) {
        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring(7);
    }
}
