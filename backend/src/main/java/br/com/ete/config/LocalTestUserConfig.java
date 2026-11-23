package br.com.ete.config;

import br.com.ete.user.AppUser;
import br.com.ete.user.AppUserRepository;
import br.com.ete.user.UserRole;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * APENAS PARA TESTE LOCAL. Só é carregado com o perfil "local"
 * (spring.profiles.active=local); nunca ative esse perfil em produção.
 * Cria o administrador adm / 123 sem exigir troca de senha.
 */
@Configuration
@Profile("local")
public class LocalTestUserConfig {

    static final String TEST_ADMIN_USERNAME = "adm";
    static final String TEST_ADMIN_PASSWORD = "123";

    @Bean
    CommandLineRunner seedLocalTestAdmin(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (!userRepository.existsByUsernameIgnoreCase(TEST_ADMIN_USERNAME)) {
                userRepository.save(new AppUser(
                        "Administrador de Teste",
                        TEST_ADMIN_USERNAME,
                        passwordEncoder.encode(TEST_ADMIN_PASSWORD),
                        UserRole.ADMIN,
                        false
                ));
            }
        };
    }
}
