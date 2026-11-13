package br.com.carbonifera.ete.notification;

import br.com.carbonifera.ete.notification.push.PushSender;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherImplTest {

    private static final String DEDUPE_KEY = "HOURLY_MEASUREMENT_REMINDER|slot-1";

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private PushSender pushSender;

    @Test
    void dispatchCreatesOneNotificationPerRecipient() {
        NotificationDispatcherImpl dispatcher = dispatcher();
        AppUser first = user();
        AppUser second = user();
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Notification> created = dispatcher.dispatch(command(List.of(first, second), null));

        assertThat(created).hasSize(2);
        assertThat(created).extracting(Notification::getTargetUser).containsExactly(first, second);
        assertThat(created).allSatisfy(notification -> assertThat(notification.getSentAt()).isNotNull());
        verify(notificationRepository, times(2)).save(any(Notification.class));
        verify(pushSender, times(2)).send(any(AppUser.class), any());
    }

    @Test
    void dispatchSkipsRecipientWhenDedupeKeyAlreadyExists() {
        NotificationDispatcherImpl dispatcher = dispatcher();
        AppUser recipient = user();
        when(notificationRepository.existsByTargetUserAndTypeAndDedupeKey(
                recipient, NotificationType.HOURLY_MEASUREMENT_REMINDER, DEDUPE_KEY)).thenReturn(true);

        List<Notification> created = dispatcher.dispatch(command(List.of(recipient), DEDUPE_KEY));

        assertThat(created).isEmpty();
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(pushSender, never()).send(any(AppUser.class), any());
    }

    @Test
    void dispatchSecondRunWithSameDedupeKeyCreatesNoAdditionalNotification() {
        NotificationDispatcherImpl dispatcher = dispatcher();
        AppUser recipient = user();
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificationRepository.existsByTargetUserAndTypeAndDedupeKey(
                recipient, NotificationType.HOURLY_MEASUREMENT_REMINDER, DEDUPE_KEY))
                .thenReturn(false)
                .thenReturn(true);

        List<Notification> firstRun = dispatcher.dispatch(command(List.of(recipient), DEDUPE_KEY));
        List<Notification> secondRun = dispatcher.dispatch(command(List.of(recipient), DEDUPE_KEY));

        assertThat(firstRun).hasSize(1);
        assertThat(secondRun).isEmpty();
        verify(notificationRepository).save(any(Notification.class));
        verify(pushSender).send(any(AppUser.class), any());
    }

    @Test
    void dispatchPersistsNotificationEvenWhenPushSenderFails() {
        NotificationDispatcherImpl dispatcher = dispatcher();
        AppUser recipient = user();
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("push down")).when(pushSender).send(any(AppUser.class), any());

        List<Notification> created = dispatcher.dispatch(command(List.of(recipient), null));

        assertThat(created).hasSize(1);
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void dispatchWithNoRecipientsIsNoOp() {
        NotificationDispatcherImpl dispatcher = dispatcher();

        List<Notification> created = dispatcher.dispatch(command(List.of(), null));

        assertThat(created).isEmpty();
        verify(notificationRepository, never()).save(any(Notification.class));
        verify(pushSender, never()).send(any(AppUser.class), any());
    }

    private NotificationDispatcherImpl dispatcher() {
        return new NotificationDispatcherImpl(notificationRepository, pushSender);
    }

    private NotificationCommand command(List<AppUser> recipients, String dedupeKey) {
        return new NotificationCommand(
                NotificationType.HOURLY_MEASUREMENT_REMINDER,
                recipients,
                null,
                "Coleta e aferições",
                "Não esqueça de fazer as aferições de pH e vazão",
                "/app/medicoes/ph",
                dedupeKey,
                null
        );
    }

    private AppUser user() {
        AppUser user = new AppUser("User", "user-" + UUID.randomUUID(), "hash", UserRole.OPERATOR, false);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
