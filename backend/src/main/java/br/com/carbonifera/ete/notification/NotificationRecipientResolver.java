package br.com.carbonifera.ete.notification;

import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.AppUserRepository;
import br.com.carbonifera.ete.user.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

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
    public List<AppUser> activeOperators() {
        return userRepository.findAllByRoleAndActiveTrue(UserRole.OPERATOR);
    }
}
