package br.com.carbonifera.ete.notification.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PushSubscriptionDeleteRequest(@NotBlank @Size(max = 500) String endpoint) {
}
