package br.com.carbonifera.ete.notification;

import java.util.List;

public interface NotificationDispatcher {

    List<Notification> dispatch(NotificationCommand command);
}
