package br.com.ete.config;

import br.com.ete.station.Station;
import br.com.ete.station.StationRepository;
import br.com.ete.user.AppUser;
import br.com.ete.user.AppUserRepository;
import br.com.ete.user.UserRole;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class BootstrapDataConfig {

    @Bean
    CommandLineRunner seedStationsAndAdmin(
            StationRepository stationRepository,
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            InitialAdminProperties initialAdminProperties,
            StationSeedProperties stationSeedProperties
    ) {
        return args -> {
            seedStation(stationRepository, stationSeedProperties.empresa1Name(), "ete-empresa-1");
            seedStation(stationRepository, stationSeedProperties.empresa2Name(), "ete-empresa-2");
            seedInitialAdmin(userRepository, passwordEncoder, initialAdminProperties);
        };
    }

    private void seedStation(StationRepository stationRepository, String name, String slug) {
        if (!stationRepository.existsBySlug(slug)) {
            stationRepository.save(new Station(name, slug));
        }
    }

    private void seedInitialAdmin(
            AppUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            InitialAdminProperties properties
    ) {
        if (!userRepository.existsByUsernameIgnoreCase(properties.username())) {
            AppUser admin = new AppUser(
                    properties.name(),
                    properties.username(),
                    passwordEncoder.encode(properties.password()),
                    UserRole.ADMIN,
                    true
            );
            userRepository.save(admin);
        }
    }
}
