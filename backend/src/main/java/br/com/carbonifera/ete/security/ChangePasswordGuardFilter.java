package br.com.carbonifera.ete.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ChangePasswordGuardFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (mustChangePassword(authentication) && isProtectedApi(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Password change required");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean mustChangePassword(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof UserPrincipal principal
                && principal.isChangePassword();
    }

    private boolean isProtectedApi(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/")
                && !path.equals("/api/auth/change-password")
                && !path.equals("/api/auth/login")
                && !path.equals("/api/auth/refresh");
    }
}
