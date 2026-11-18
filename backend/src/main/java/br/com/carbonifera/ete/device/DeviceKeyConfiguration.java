package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.config.DeviceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class DeviceKeyConfiguration {
    @Bean
    DeviceKeyHasher deviceKeyHasher() {
        return new DeviceKeyHasher();
    }

    @Bean
    DeviceKeyGenerator deviceKeyGenerator() {
        return new DeviceKeyGenerator();
    }

    @Bean
    DeviceKeyCipher deviceKeyCipher(DeviceProperties properties) {
        return new DeviceKeyCipher(properties);
    }
}
