package br.com.carbonifera.ete.notification;

import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications", uniqueConstraints = @UniqueConstraint(
        name = "uk_notification_dedupe",
        columnNames = {"target_user_id", "type", "dedupe_key"}))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "station_id")
    private Station station;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id")
    private AppUser targetUser;

    @Column(nullable = false, length = 140)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(length = 300)
    private String targetUrl;

    private Instant scheduledAt;

    private Instant sentAt;

    private Instant readAt;

    @Column(length = 80)
    private String externalProvider;

    @Column(length = 120)
    private String externalStatus;

    @Column(name = "dedupe_key", length = 120)
    private String dedupeKey;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(
            NotificationType type,
            Station station,
            AppUser targetUser,
            String title,
            String message,
            String targetUrl,
            Instant scheduledAt
    ) {
        this(type, station, targetUser, title, message, targetUrl, scheduledAt, null);
    }

    public Notification(
            NotificationType type,
            Station station,
            AppUser targetUser,
            String title,
            String message,
            String targetUrl,
            Instant scheduledAt,
            String dedupeKey
    ) {
        this.type = type;
        this.station = station;
        this.targetUser = targetUser;
        this.title = title;
        this.message = message;
        this.targetUrl = targetUrl;
        this.scheduledAt = scheduledAt;
        this.dedupeKey = dedupeKey;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public void markRead(Instant readAt) {
        this.readAt = readAt;
    }

    public void markSent(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public UUID getId() {
        return id;
    }

    public NotificationType getType() {
        return type;
    }

    public Station getStation() {
        return station;
    }

    public AppUser getTargetUser() {
        return targetUser;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public String getExternalProvider() {
        return externalProvider;
    }

    public String getExternalStatus() {
        return externalStatus;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
