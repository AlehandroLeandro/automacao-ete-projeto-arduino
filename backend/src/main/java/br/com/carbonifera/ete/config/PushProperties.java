package br.com.carbonifera.ete.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.push.vapid")
public record PushProperties(String publicKey, String privateKey, String subject) {

    public PushProperties {
        publicKey = clean(publicKey);
        privateKey = clean(privateKey);
        subject = clean(subject);
    }

    public boolean hasKeys() {
        return !publicKey.isBlank() && !privateKey.isBlank() && !subject.isBlank();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
