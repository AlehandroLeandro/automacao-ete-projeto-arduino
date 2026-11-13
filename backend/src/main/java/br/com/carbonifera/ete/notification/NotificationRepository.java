package br.com.carbonifera.ete.notification;

import br.com.carbonifera.ete.user.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsByTargetUserAndTypeAndDedupeKey(AppUser targetUser, NotificationType type, String dedupeKey);

    @Query("""
            select notification from Notification notification
            where (notification.targetUser.id = :userId or notification.targetUser is null)
              and (:stationId is null or notification.station is null or notification.station.id = :stationId)
              and (:unreadOnly = false or notification.readAt is null)
            order by coalesce(notification.scheduledAt, notification.createdAt) desc
            """)
    List<Notification> findVisibleToUser(
            @Param("userId") UUID userId,
            @Param("stationId") UUID stationId,
            @Param("unreadOnly") boolean unreadOnly
    );
}
