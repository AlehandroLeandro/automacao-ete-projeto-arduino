package br.com.carbonifera.ete.measurement;

import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class FlowAuthorshipRepositoryTest {

    private static final Instant START_AT = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant END_AT = Instant.parse("2026-07-02T00:00:00Z");
    private static final Instant EARLIER_AT = Instant.parse("2026-07-01T08:00:00Z");
    private static final Instant LATER_AT = Instant.parse("2026-07-01T10:00:00Z");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private FlowMeasurementRepository repository;

    private Station station;
    private AppUser operatorA;
    private AppUser operatorB;

    @BeforeEach
    void setUp() {
        station = new Station("Alpha Station", "alpha-station");
        entityManager.persist(station);
        operatorA = saveUser("operator-a", UserRole.OPERATOR);
        operatorB = saveUser("operator-b", UserRole.OPERATOR);
    }

    @Test
    void authorFilterReturnsOnlyRecordsCreatedByThatAuthor() {
        FlowMeasurement ownedByA = save(operatorA, LATER_AT);
        save(operatorB, EARLIER_AT);

        List<FlowMeasurement> result = repository.findForList(
                List.of(station),
                START_AT,
                END_AT,
                false,
                operatorA.getId()
        );

        assertThat(result)
                .extracting(FlowMeasurement::getId)
                .containsExactly(ownedByA.getId());
    }

    @Test
    void nullAuthorFilterReturnsRecordsFromEveryAuthorInMeasuredAtDescOrder() {
        FlowMeasurement olderFromA = save(operatorA, EARLIER_AT);
        FlowMeasurement newerFromB = save(operatorB, LATER_AT);

        List<FlowMeasurement> result = repository.findForList(
                List.of(station),
                START_AT,
                END_AT,
                false,
                null
        );

        assertThat(result)
                .extracting(FlowMeasurement::getId)
                .containsExactly(newerFromB.getId(), olderFromA.getId());
    }

    @Test
    void authorFilterPreservesMeasuredAtDescOrderAmongOwnRecords() {
        FlowMeasurement olderFromA = save(operatorA, EARLIER_AT);
        FlowMeasurement newerFromA = save(operatorA, LATER_AT);
        save(operatorB, LATER_AT.plusSeconds(60));

        List<FlowMeasurement> result = repository.findForList(
                List.of(station),
                START_AT,
                END_AT,
                false,
                operatorA.getId()
        );

        assertThat(result)
                .extracting(FlowMeasurement::getId)
                .containsExactly(newerFromA.getId(), olderFromA.getId());
    }

    @Test
    void authorFilterWithIncludeDeletedReturnsOnlyOwnDeletedRecords() {
        FlowMeasurement deletedFromA = saveDeleted(operatorA, LATER_AT);
        FlowMeasurement activeFromA = save(operatorA, EARLIER_AT);
        FlowMeasurement deletedFromB = saveDeleted(operatorB, LATER_AT.plusSeconds(60));

        List<FlowMeasurement> result = repository.findForList(
                List.of(station),
                START_AT,
                END_AT,
                true,
                operatorA.getId()
        );

        assertThat(result)
                .extracting(FlowMeasurement::getId)
                .containsExactly(deletedFromA.getId(), activeFromA.getId())
                .doesNotContain(deletedFromB.getId());
    }

    private AppUser saveUser(String username, UserRole role) {
        AppUser user = new AppUser(username, username, "hash", role, station, false);
        entityManager.persist(user);
        return user;
    }

    private FlowMeasurement save(AppUser createdBy, Instant measuredAt) {
        FlowMeasurement measurement = new FlowMeasurement(station, new BigDecimal("100.00"), measuredAt, createdBy);
        entityManager.persistAndFlush(measurement);
        return measurement;
    }

    private FlowMeasurement saveDeleted(AppUser createdBy, Instant measuredAt) {
        FlowMeasurement measurement = save(createdBy, measuredAt);
        measurement.softDelete(createdBy);
        entityManager.flush();
        return measurement;
    }
}
