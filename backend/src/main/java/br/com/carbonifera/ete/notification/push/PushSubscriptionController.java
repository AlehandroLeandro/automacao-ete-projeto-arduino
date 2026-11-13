package br.com.carbonifera.ete.notification.push;

import br.com.carbonifera.ete.config.PushProperties;
import br.com.carbonifera.ete.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/push")
public class PushSubscriptionController {

    private final PushProperties properties;
    private final PushSubscriptionService service;

    public PushSubscriptionController(PushProperties properties, PushSubscriptionService service) {
        this.properties = properties;
        this.service = service;
    }

    @GetMapping("/public-key")
    public PushPublicKeyResponse publicKey() {
        return new PushPublicKeyResponse(properties.publicKey());
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public PushSubscriptionResponse subscribe(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PushSubscriptionRequest request
    ) {
        service.upsert(principal, request);
        return new PushSubscriptionResponse(request.endpoint(), true);
    }

    @DeleteMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody PushSubscriptionDeleteRequest request
    ) {
        service.deactivate(principal, request.endpoint());
    }
}
