package br.com.carbonifera.ete.security;

import br.com.carbonifera.ete.common.error.BusinessException;
import br.com.carbonifera.ete.common.error.ResourceNotFoundException;
import br.com.carbonifera.ete.config.OperatorAccessProperties;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.AppUserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AppUserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final OperatorAccessProperties operatorAccessProperties;

    public AuthService(
            AuthenticationManager authenticationManager,
            AppUserRepository userRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            OperatorAccessProperties operatorAccessProperties
    ) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.operatorAccessProperties = operatorAccessProperties;
    }

    @Transactional(readOnly = true)
    public AuthTokensResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password())
        );
        AppUser user = requireActiveUser(request.username());
        return tokensFor(user);
    }

    @Transactional(readOnly = true)
    public AuthTokensResponse refresh(RefreshTokenRequest request) {
        if (!jwtService.isRefreshToken(request.refreshToken())) {
            throw new BusinessException("Invalid refresh token");
        }
        AppUser user = requireActiveUser(jwtService.subject(request.refreshToken()));
        return tokensFor(user);
    }

    @Transactional
    public void changePassword(UserPrincipal principal, ChangePasswordRequest request) {
        PasswordPolicy.validatePermanentPassword(request.newPassword(), request.confirmPassword());
        AppUser user = requireActiveUser(principal.getUsername());
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("Current password is invalid");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setChangePassword(false);
    }

    private AppUser requireActiveUser(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .filter(AppUser::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private AuthTokensResponse tokensFor(AppUser user) {
        return new AuthTokensResponse(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user),
                "Bearer",
                user.getId(),
                user.getName(),
                user.getUsername(),
                user.getRole(),
                user.getStation() == null ? null : user.getStation().getId(),
                user.getStation() == null ? null : user.getStation().getSlug(),
                user.getStation() == null ? null : user.getStation().getName(),
                user.isChangePassword(),
                operatorAccessProperties.recordWindowHours()
        );
    }
}
