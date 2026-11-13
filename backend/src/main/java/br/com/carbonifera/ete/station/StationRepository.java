package br.com.carbonifera.ete.station;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StationRepository extends JpaRepository<Station, UUID> {

    List<Station> findAllByActiveTrueOrderByNameAsc();

    Optional<Station> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
