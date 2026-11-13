package br.com.carbonifera.ete.user;

import br.com.carbonifera.ete.common.error.BusinessException;
import br.com.carbonifera.ete.common.error.ResourceNotFoundException;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.station.StationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final AppUserRepository userRepository;
    private final StationService stationService;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator temporaryPasswordGenerator;

    public UserService(
            AppUserRepository userRepository,
            StationService stationService,
            PasswordEncoder passwordEncoder,
            TemporaryPasswordGenerator temporaryPasswordGenerator
    ) {
        this.userRepository = userRepository;
        this.stationService = stationService;
        this.passwordEncoder = passwordEncoder;
        this.temporaryPasswordGenerator = temporaryPasswordGenerator;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAll()
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserDirectoryEntry> listDirectory() {
        return userRepository.findAllByActiveTrue()
                .stream()
                .map(UserDirectoryEntry::from)
                .toList();
    }

    @Transactional
    public UserCreatedResponse create(UserCreateRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new BusinessException("Username already exists");
        }

        String temporaryPassword = temporaryPasswordGenerator.generate();
        Station station = resolveStation(request.role(), request.stationId());
        AppUser user = new AppUser(
                request.name(),
                request.username(),
                passwordEncoder.encode(temporaryPassword),
                request.role(),
                station,
                true
        );

        AppUser saved = userRepository.save(user);
        return new UserCreatedResponse(UserResponse.from(saved), temporaryPassword);
    }

    @Transactional
    public UserResponse update(UUID userId, UserUpdateRequest request) {
        AppUser user = requireUser(userId);
        Station station = resolveStation(request.role(), request.stationId());
        user.setName(request.name());
        user.setRole(request.role());
        user.setStation(station);
        user.setActive(request.active());
        return UserResponse.from(user);
    }

    @Transactional
    public UserCreatedResponse resetPassword(UUID userId) {
        AppUser user = requireUser(userId);
        String temporaryPassword = temporaryPasswordGenerator.generate();
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setChangePassword(true);
        return new UserCreatedResponse(UserResponse.from(user), temporaryPassword);
    }

    private AppUser requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private Station resolveStation(UserRole role, UUID stationId) {
        if (!role.isStationBound()) {
            return null;
        }
        if (stationId == null) {
            throw new BusinessException("User must be linked to a station");
        }
        return stationService.requireStation(stationId);
    }
}
