package br.com.carbonifera.ete.lime;

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
class LimeAuthorshipRepositoryTest {

    private static final Instant START_AT = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant END_AT = Instant.parse("2026-07-02T00:00:00Z");
    private static final Instant EARLIER_AT = Instant.parse("2026-07-01T08:00:00Z");
    private static final Instant LATER_AT = Instant.parse("2026-07-01T10:00:00Z");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private LimeRecordRepository repository;

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
        LimeRecord ownedByA = save(operatorA, LimeRecordType.CONSUMPTION, LATER_AT, new BigDecimal("4.00"));
        save(operatorB, LimeRecordType.CONSUMPTION, EARLIER_AT, new BigDecimal("5.00"));

        List<LimeRecord> result = repository.findForList(
                List.of(station),
                null,
                START_AT,
                END_AT,
                operatorA.getId()
        );

        assertThat(result)
                .extracting(LimeRecord::getId)
                .containsExactly(ownedByA.getId());
    }

    @Test
    void nullAuthorFilterReturnsRecordsFromEveryAuthorInRecordedAtDescOrder() {
        LimeRecord olderFromA = save(operatorA, LimeRecordType.CONSUMPTION, EARLIER_AT, new BigDecimal("4.00"));
        LimeRecord newerFromB = save(operatorB, LimeRecordType.CONSUMPTION, LATER_AT, new BigDecimal("5.00"));

        List<LimeRecord> result = repository.findForList(
                List.of(station),
                null,
                START_AT,
                END_AT,
                null
        );

        assertThat(result)
                .extracting(LimeRecord::getId)
                .containsExactly(newerFromB.getId(), olderFromA.getId());
    }

    @Test
    void typeAndAuthorFiltersAreCombinedAndKeepRecordedAtDescOrder() {
        LimeRecord newerEntryFromA = save(operatorA, LimeRecordType.ENTRY, LATER_AT, new BigDecimal("10.00"));
        LimeRecord olderEntryFromA = save(operatorA, LimeRecordType.ENTRY, EARLIER_AT, new BigDecimal("20.00"));
        save(operatorA, LimeRecordType.CONSUMPTION, LATER_AT.plusSeconds(60), new BigDecimal("4.00"));
        save(operatorB, LimeRecordType.ENTRY, LATER_AT.plusSeconds(120), new BigDecimal("30.00"));

        List<LimeRecord> result = repository.findForList(
                List.of(station),
                LimeRecordType.ENTRY,
                START_AT,
                END_AT,
                operatorA.getId()
        );

        assertThat(result)
                .extracting(LimeRecord::getId)
                .containsExactly(newerEntryFromA.getId(), olderEntryFromA.getId());
    }

    @Test
    void stockQueriesStillReturnRecordsFromEveryAuthor() {
        LimeRecord entryFromA = save(operatorA, LimeRecordType.ENTRY, EARLIER_AT, new BigDecimal("10.00"));
        LimeRecord consumptionFromB = save(operatorB, LimeRecordType.CONSUMPTION, LATER_AT, new BigDecimal("4.00"));

        List<LimeRecord> asOf = repository.findAllByStationAndRecordedAtLessThanEqualOrderByRecordedAtAsc(station, END_AT);
        List<LimeRecord> all = repository.findAllByStationOrderByRecordedAtAsc(station);

        assertThat(asOf)
                .extracting(LimeRecord::getId)
                .containsExactly(entryFromA.getId(), consumptionFromB.getId());
        assertThat(all)
                .extracting(LimeRecord::getId)
                .containsExactly(entryFromA.getId(), consumptionFromB.getId());
    }

    @Test
    void consumptionSumStillAggregatesEveryAuthor() {
        save(operatorA, LimeRecordType.CONSUMPTION, EARLIER_AT, new BigDecimal("4.00"));
        save(operatorB, LimeRecordType.CONSUMPTION, LATER_AT, new BigDecimal("6.00"));

        BigDecimal total = repository.sumConsumption(station, START_AT, END_AT);

        assertThat(total).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    private AppUser saveUser(String username) {
        AppUser user = new AppUser(username, username, "hash", UserRole.OPERATOR, station, false);
        entityManager.persist(user);
        return user;
    }

    private LimeRecord save(AppUser createdBy, LimeRecordType type, Instant recordedAt, BigDecimal quantityBags) {
        LimeRecord record = new LimeRecord(station, type, quantityBags, recordedAt, "Test record", createdBy);
        entityManager.persistAndFlush(record);
        return record;
    }
}
