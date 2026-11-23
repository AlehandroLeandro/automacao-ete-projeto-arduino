package br.com.ete.notification;

import br.com.ete.station.Station;
import br.com.ete.user.AppUser;
import br.com.ete.user.AppUserRepository;
import br.com.ete.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationRecipientResolver {

    private final AppUserRepository userRepository;

    public NotificationRecipientResolver(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AppUser> activeSupervisors() {
        return userRepository.findAllByRoleAndActiveTrue(UserRole.SUPERVISOR);
    }

    @Transactional(readOnly = true)
    public List<AppUser> activeSupervisorsAndAdmins() {
        return userRepository.findAllByRoleInAndActiveTrue(List.of(UserRole.SUPERVISOR, UserRole.ADMIN));
    }

    @Transactional(readOnly = true)
    public List<AppUser> stoppageAlertRecipients(Station station) {
        List<AppUser> recipients = new ArrayList<>(activeSupervisors());
        recipients.addAll(userRepository.findAllByRoleAndStationAndActiveTrue(UserRole.VIEWER, station));
        return recipients;
    }

    @Transactional(readOnly = true)
    public List<AppUser> deviceAlertRecipients(Station station) {
        LinkedHashMap<UUID, AppUser> recipients = new LinkedHashMap<>();
        activeSupervisorsAndAdmins().forEach(user -> recipients.putIfAbsent(user.getId(), user));
        userRepository.findAllByRoleAndStationAndActiveTrue(UserRole.OPERATOR, station)
                .forEach(user -> recipients.putIfAbsent(user.getId(), user));
        return new ArrayList<>(recipients.values());
    }

    @Transactional(readOnly = true)
    public List<AppUser> activeOperators() {
        return userRepository.findAllByRoleAndActiveTrue(UserRole.OPERATOR);
    }
}
