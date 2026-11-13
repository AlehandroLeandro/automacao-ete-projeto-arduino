package br.com.carbonifera.ete.notification.push;

import br.com.carbonifera.ete.user.AppUser;

public interface PushSender {

    void send(AppUser user, PushPayload payload);
}
