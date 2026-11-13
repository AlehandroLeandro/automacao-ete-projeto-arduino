package br.com.carbonifera.ete.security;

import br.com.carbonifera.ete.common.error.ResourceNotFoundException;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {

    private final AppUserRepository userRepository;

    public CurrentUserService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AppUser requireUser(UserPrincipal principal) {
        return userRepository.findById(principal.getId())
                .filter(AppUser::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
