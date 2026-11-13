package br.com.carbonifera.ete.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.util.Set;

@Configuration
public class SensitiveConfigurationValidator {

    private static final Set<String> FORBIDDEN_PLACEHOLDERS = Set.of(
            "change_me",
            "admin12345",
            "replace_with_database_password",
            "replace_with_a_random_secret_with_at_least_32_characters",
            "replace_with_initial_admin_password",
            "change_this_to_a_very_long_random_secret_with_at_least_32_chars"
    );

    @Bean
    CommandLineRunner validateSensitiveConfiguration(
            JwtProperties jwtProperties,
            InitialAdminProperties initialAdminProperties,
            Environment environment
    ) {
        return args -> {
            rejectPlaceholder("DB_PASSWORD", environment.getRequiredProperty("DB_PASSWORD"));
            rejectPlaceholder("JWT_SECRET", jwtProperties.secret());
            rejectPlaceholder("INITIAL_ADMIN_PASSWORD", initialAdminProperties.password());
        };
    }

    private void rejectPlaceholder(String variableName, String value) {
        if (FORBIDDEN_PLACEHOLDERS.contains(value)) {
            throw new IllegalStateException(variableName + " must be changed before starting the application");
        }
    }
}
