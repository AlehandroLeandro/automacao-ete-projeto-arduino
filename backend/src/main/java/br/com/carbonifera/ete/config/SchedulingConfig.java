package br.com.carbonifera.ete.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableScheduling
public class SchedulingConfig {

    /**
     * Application timezone used by scheduled jobs (Stream B crons) and time formatting.
     */
    public static final ZoneId APP_ZONE = ZoneId.of("America/Sao_Paulo");

    @Bean
    Clock appClock() {
        return Clock.system(APP_ZONE);
    }
}
