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
class PhAuthorshipRepositoryTest {

    private static final Instant START_AT = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant END_AT = Instant.parse("2026-07-02T00:00:00Z");
    private static final Instant EARLIER_AT = Instant.parse("2026-07-01T08:00:00Z");
    private static final Instant LATER_AT = Instant.parse("2026-07-01T10:00:00Z");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PhMeasurementRepository repository;

    private Station station;
    private AppUser operatorA;
    private AppUser operatorB;

    @BeforeEach
    void setUp() {
        station = new Station("Alpha Station", "alpha-station");
        entityManager.persist(station);
        operatorA = saveUser("operator-a");
        operatorB = saveUser("operator-b");
    }

    @Test
    void authorFilterReturnsOnlyRecordsCreatedByThatAuthor() {
        PhMeasurement ownedByA = save(operatorA, PhWaterType.RAW, LATER_AT);
        save(operatorB, PhWaterType.RAW, EARLIER_AT);

        List<PhMeasurement> result = repository.findForList(
                List.of(station),
                null,
                START_AT,
                END_AT,
                false,
                operatorA.getId()
        );

        assertThat(result)
                .extracting(PhMeasurement::getId)
                .containsExactly(ownedByA.getId());
    }

    @Test
    void nullAuthorFilterReturnsRecordsFromEveryAuthorInMeasuredAtDescOrder() {
        PhMeasurement olderFromA = save(operatorA, PhWaterType.RAW, EARLIER_AT);
        PhMeasurement newerFromB = save(operatorB, PhWaterType.RAW, LATER_AT);

        List<PhMeasurement> result = repository.findForList(
                List.of(station),
                null,
                START_AT,
                END_AT,
                false,
                null
        );

        assertThat(result)
                .extracting(PhMeasurement::getId)
                .containsExactly(newerFromB.getId(), olderFromA.getId());
    }

    @Test
    void typeAndAuthorFiltersAreCombined() {
        PhMeasurement treatedFromA = save(operatorA, PhWaterType.TREATED, LATER_AT);
        save(operatorA, PhWaterType.RAW, LATER_AT.plusSeconds(60));
        save(operatorB, PhWaterType.TREATED, LATER_AT.plusSeconds(120));

        List<PhMeasurement> result = repository.findForList(
                List.of(station),
                PhWaterType.TREATED,
                START_AT,
                END_AT,
                false,
                operatorA.getId()
        );

        assertThat(result)
                .extracting(PhMeasurement::getId)
                .containsExactly(treatedFromA.getId());
    }

    @Test
    void authorFilterPreservesMeasuredAtDescOrderAmongOwnRecords() {
        PhMeasurement olderFromA = save(operatorA, PhWaterType.RAW, EARLIER_AT);
        PhMeasurement newerFromA = save(operatorA, PhWaterType.RAW, LATER_AT);
        save(operatorB, PhWaterType.RAW, LATER_AT.plusSeconds(60));

        List<PhMeasurement> result = repository.findForList(
                List.of(station),
                null,
                START_AT,
                END_AT,
                false,
                operatorA.getId()
        );

        assertThat(result)
                .extracting(PhMeasurement::getId)
                .containsExactly(newerFromA.getId(), olderFromA.getId());
    }

    @Test
    void authorFilterWithIncludeDeletedReturnsOnlyOwnDeletedRecords() {
        PhMeasurement deletedFromA = saveDeleted(operatorA, PhWaterType.RAW, LATER_AT);
        PhMeasurement activeFromA = save(operatorA, PhWaterType.RAW, EARLIER_AT);
        PhMeasurement deletedFromB = saveDeleted(operatorB, PhWaterType.RAW, LATER_AT.plusSeconds(60));

        List<PhMeasurement> result = repository.findForList(
                List.of(station),
                null,
                START_AT,
                END_AT,
                true,
                operatorA.getId()
        );

        assertThat(result)
                .extracting(PhMeasurement::getId)
                .containsExactly(deletedFromA.getId(), activeFromA.getId())
                .doesNotContain(deletedFromB.getId());
    }

    private AppUser saveUser(String username) {
        AppUser user = new AppUser(username, username, "hash", UserRole.OPERATOR, station, false);
        entityManager.persist(user);
        return user;
    }

    private PhMeasurement save(AppUser createdBy, PhWaterType type, Instant measuredAt) {
        PhMeasurement measurement = new PhMeasurement(station, type, new BigDecimal("7.20"), measuredAt, createdBy);
        entityManager.persistAndFlush(measurement);
        return measurement;
    }

    private PhMeasurement saveDeleted(AppUser createdBy, PhWaterType type, Instant measuredAt) {
        PhMeasurement measurement = save(createdBy, type, measuredAt);
        measurement.softDelete(createdBy);
        entityManager.flush();
        return measurement;
    }
}
