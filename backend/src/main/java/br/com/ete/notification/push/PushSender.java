package br.com.ete.notification.push;

import br.com.ete.user.AppUser;

public interface PushSender {

    void send(AppUser user, PushPayload payload);
}
