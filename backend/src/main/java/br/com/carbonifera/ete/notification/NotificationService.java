package br.com.carbonifera.ete.notification;

import br.com.carbonifera.ete.common.error.ResourceNotFoundException;
import br.com.carbonifera.ete.security.UserPrincipal;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.station.StationScopeService;
import br.com.carbonifera.ete.station.StationService;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.AppUserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final StationService stationService;
    private final StationScopeService stationScopeService;
    private final AppUserRepository userRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            StationService stationService,
            StationScopeService stationScopeService,
            AppUserRepository userRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.stationService = stationService;
        this.stationScopeService = stationScopeService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(UserPrincipal principal, boolean unreadOnly) {
        UUID stationId = stationScopeService.stationFilterForList(principal);
        return notificationRepository.findVisibleToUser(principal.getId(), stationId, unreadOnly)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional
    public NotificationResponse create(NotificationCreateRequest request) {
        Station station = request.stationId() == null ? null : stationService.requireStation(request.stationId());
        AppUser targetUser = request.targetUserId() == null ? null : requireUser(request.targetUserId());
        Notification saved = notificationRepository.save(new Notification(
                request.type(),
                station,
                targetUser,
                request.title(),
                request.message(),
                request.targetUrl(),
                request.scheduledAt()
        ));
        return NotificationResponse.from(saved);
    }

    @Transactional
    public NotificationResponse markRead(UUID notificationId, UserPrincipal principal) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (notification.getTargetUser() != null && !notification.getTargetUser().getId().equals(principal.getId())) {
            throw new AccessDeniedException("Notification does not belong to current user");
        }
        if (notification.getStation() != null) {
            stationScopeService.requireReadableStation(notification.getStation(), principal);
        }
        notification.markRead(Instant.now());
        return NotificationResponse.from(notification);
    }

    private AppUser requireUser(UUID userId) {
        return userRepository.findById(userId)
                .filter(AppUser::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
