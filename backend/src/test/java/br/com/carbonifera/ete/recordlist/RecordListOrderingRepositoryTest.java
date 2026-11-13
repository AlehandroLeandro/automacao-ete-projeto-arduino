package br.com.carbonifera.ete.recordlist;

import br.com.carbonifera.ete.lime.LimeRecord;
import br.com.carbonifera.ete.lime.LimeRecordRepository;
import br.com.carbonifera.ete.lime.LimeRecordType;
import br.com.carbonifera.ete.measurement.FlowMeasurement;
import br.com.carbonifera.ete.measurement.FlowMeasurementRepository;
import br.com.carbonifera.ete.measurement.PhMeasurement;
import br.com.carbonifera.ete.measurement.PhMeasurementRepository;
import br.com.carbonifera.ete.measurement.PhWaterType;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.stoppage.Stoppage;
import br.com.carbonifera.ete.stoppage.StoppageReason;
import br.com.carbonifera.ete.stoppage.StoppageRepository;
import br.com.carbonifera.ete.stoppage.StoppageStatus;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class RecordListOrderingRepositoryTest {

    private static final Instant START_AT = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant END_AT = Instant.parse("2026-07-02T00:00:00Z");
    private static final Instant NEWEST_AT = Instant.parse("2026-07-01T10:00:00Z");
    private static final Instant TIED_AT = Instant.parse("2026-07-01T09:00:00Z");
    private static final Instant OLDEST_AT = Instant.parse("2026-07-01T08:00:00Z");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private FlowMeasurementRepository flowMeasurements;

    @Autowired
    private PhMeasurementRepository phMeasurements;

    @Autowired
    private LimeRecordRepository limeRecords;

    @Autowired
    private StoppageRepository stoppages;

    @Test
    void flowMeasurementsForListAreOrderedByMeasuredAtDescThenIdDescAndKeepFilters() {
        TestContext context = createContext();
        FlowMeasurement oldest = saveFlow(context.alphaStation(), OLDEST_AT);
        FlowMeasurement tieA = saveFlow(context.alphaStation(), TIED_AT);
        FlowMeasurement tieB = saveFlow(context.alphaStation(), TIED_AT);
        FlowMeasurement newest = saveFlow(context.zuluStation(), NEWEST_AT);
        saveFlow(context.extraStation(), NEWEST_AT.plusSeconds(60));
        FlowMeasurement deleted = saveDeletedFlow(context.alphaStation(), NEWEST_AT.plusSeconds(120), context.user());

        List<FlowMeasurement> result = flowMeasurements.findForList(
                List.of(context.alphaStation(), context.zuluStation()),
                START_AT,
                END_AT,
                false,
                null
        );

        assertThat(result)
                .extracting(FlowMeasurement::getId)
                .containsExactly(
                        newest.getId(),
                        firstIdDescending(tieA, tieB, FlowMeasurement::getId),
                        secondIdDescending(tieA, tieB, FlowMeasurement::getId),
                        oldest.getId()
                );

        List<FlowMeasurement> resultWithDeleted = flowMeasurements.findForList(
                List.of(context.alphaStation(), context.zuluStation()),
                START_AT,
                END_AT,
                true,
                null
        );

        assertThat(resultWithDeleted)
                .extracting(FlowMeasurement::getId)
                .containsExactly(
                        deleted.getId(),
                        newest.getId(),
                        firstIdDescending(tieA, tieB, FlowMeasurement::getId),
                        secondIdDescending(tieA, tieB, FlowMeasurement::getId),
                        oldest.getId()
                );
    }

    @Test
    void phMeasurementsForListAreOrderedByMeasuredAtDescThenIdDescAndKeepFilters() {
        TestContext context = createContext();
        PhMeasurement oldest = savePh(context.alphaStation(), PhWaterType.RAW, OLDEST_AT);
        PhMeasurement tieA = savePh(context.alphaStation(), PhWaterType.RAW, TIED_AT);
        PhMeasurement tieB = savePh(context.alphaStation(), PhWaterType.RAW, TIED_AT);
        PhMeasurement newest = savePh(context.zuluStation(), PhWaterType.RAW, NEWEST_AT);
        savePh(context.zuluStation(), PhWaterType.TREATED, NEWEST_AT.plusSeconds(60));
        savePh(context.extraStation(), PhWaterType.RAW, NEWEST_AT.plusSeconds(120));
        PhMeasurement deleted = saveDeletedPh(context.alphaStation(), PhWaterType.RAW, NEWEST_AT.plusSeconds(180), context.user());

        List<PhMeasurement> result = phMeasurements.findForList(
                List.of(context.alphaStation(), context.zuluStation()),
                PhWaterType.RAW,
                START_AT,
                END_AT,
                false,
                null
        );

        assertThat(result)
                .extracting(PhMeasurement::getId)
                .containsExactly(
                        newest.getId(),
                        firstIdDescending(tieA, tieB, PhMeasurement::getId),
                        secondIdDescending(tieA, tieB, PhMeasurement::getId),
                        oldest.getId()
                );

        List<PhMeasurement> resultWithDeleted = phMeasurements.findForList(
                List.of(context.alphaStation(), context.zuluStation()),
                PhWaterType.RAW,
                START_AT,
                END_AT,
                true,
                null
        );

        assertThat(resultWithDeleted)
                .extracting(PhMeasurement::getId)
                .containsExactly(
                        deleted.getId(),
                        newest.getId(),
                        firstIdDescending(tieA, tieB, PhMeasurement::getId),
                        secondIdDescending(tieA, tieB, PhMeasurement::getId),
                        oldest.getId()
                );
    }

    @Test
    void limeRecordsForListAreOrderedByRecordedAtDescThenIdDescAndKeepFilters() {
        TestContext context = createContext();
        LimeRecord oldest = saveLime(context.alphaStation(), LimeRecordType.CONSUMPTION, OLDEST_AT);
        LimeRecord tieA = saveLime(context.alphaStation(), LimeRecordType.CONSUMPTION, TIED_AT);
        LimeRecord tieB = saveLime(context.alphaStation(), LimeRecordType.CONSUMPTION, TIED_AT);
        LimeRecord newest = saveLime(context.zuluStation(), LimeRecordType.CONSUMPTION, NEWEST_AT);
        saveLime(context.zuluStation(), LimeRecordType.ENTRY, NEWEST_AT.plusSeconds(60));
        saveLime(context.extraStation(), LimeRecordType.CONSUMPTION, NEWEST_AT.plusSeconds(120));

        List<LimeRecord> result = limeRecords.findForList(
                List.of(context.alphaStation(), context.zuluStation()),
                LimeRecordType.CONSUMPTION,
                START_AT,
                END_AT,
                null
        );

        assertThat(result)
                .extracting(LimeRecord::getId)
                .containsExactly(
                        newest.getId(),
                        firstIdDescending(tieA, tieB, LimeRecord::getId),
                        secondIdDescending(tieA, tieB, LimeRecord::getId),
                        oldest.getId()
                );
    }

    @Test
    void stoppagesForListAreOrderedByStartAtDescThenIdDescAndKeepFilters() {
        TestContext context = createContext();
        Stoppage oldest = saveStoppage(context.alphaStation(), OLDEST_AT);
        Stoppage tieA = saveStoppage(context.alphaStation(), TIED_AT);
        Stoppage tieB = saveStoppage(context.alphaStation(), TIED_AT);
        Stoppage newest = saveStoppage(context.zuluStation(), NEWEST_AT);
        saveStoppage(context.extraStation(), NEWEST_AT.plusSeconds(60));
        Stoppage deleted = saveDeletedStoppage(context.alphaStation(), NEWEST_AT.plusSeconds(120), context.user());
        saveFinishedStoppage(context.zuluStation(), NEWEST_AT.plusSeconds(180));

        List<Stoppage> result = stoppages.findForList(
                List.of(context.alphaStation(), context.zuluStation()),
                StoppageStatus.ACTIVE,
                START_AT,
                END_AT,
                false
        );

        assertThat(result)
                .extracting(Stoppage::getId)
                .containsExactly(
                        newest.getId(),
                        firstIdDescending(tieA, tieB, Stoppage::getId),
                        secondIdDescending(tieA, tieB, Stoppage::getId),
                        oldest.getId()
                );

        List<Stoppage> resultWithDeleted = stoppages.findForList(
                List.of(context.alphaStation(), context.zuluStation()),
                StoppageStatus.ACTIVE,
                START_AT,
                END_AT,
                true
        );

        assertThat(resultWithDeleted)
                .extracting(Stoppage::getId)
                .containsExactly(
                        deleted.getId(),
                        newest.getId(),
                        firstIdDescending(tieA, tieB, Stoppage::getId),
                        secondIdDescending(tieA, tieB, Stoppage::getId),
                        oldest.getId()
                );
    }

    @Test
    void operatorStoppageListKeepsOldActiveStoppagesButDoesNotLeakDeletedOnes() {
        TestContext context = createContext();
        Stoppage oldActive = saveStoppage(context.alphaStation(), OLDEST_AT);
        Stoppage recent = saveStoppage(context.alphaStation(), NEWEST_AT);
        Stoppage deletedOldActive = saveDeletedStoppage(context.alphaStation(), OLDEST_AT.plusSeconds(60), context.user());

        List<Stoppage> result = stoppages.findForOperatorList(
                List.of(context.alphaStation(), context.zuluStation()),
                StoppageStatus.ACTIVE,
                TIED_AT,
                END_AT,
                true
        );

        assertThat(result)
                .extracting(Stoppage::getId)
                .containsExactly(recent.getId(), oldActive.getId())
                .doesNotContain(deletedOldActive.getId());
    }

    private TestContext createContext() {
        Station alphaStation = saveStation("Alpha Station", "alpha-station");
        Station zuluStation = saveStation("Zulu Station", "zulu-station");
        Station extraStation = saveStation("Extra Station", "extra-station");
        AppUser user = saveUser(alphaStation);
        return new TestContext(alphaStation, zuluStation, extraStation, user);
    }

    private Station saveStation(String name, String slug) {
        Station station = new Station(name, slug);
        entityManager.persist(station);
        return station;
    }

    private AppUser saveUser(Station station) {
        AppUser user = new AppUser("Operator", "operator", "hash", UserRole.OPERATOR, station, false);
        entityManager.persist(user);
        return user;
    }

    private FlowMeasurement saveFlow(Station station, Instant measuredAt) {
        FlowMeasurement measurement = new FlowMeasurement(station, new BigDecimal("100.00"), measuredAt, currentUser());
        entityManager.persistAndFlush(measurement);
        return measurement;
    }

    private FlowMeasurement saveDeletedFlow(Station station, Instant measuredAt, AppUser deletedBy) {
        FlowMeasurement measurement = saveFlow(station, measuredAt);
        measurement.softDelete(deletedBy);
        entityManager.flush();
        return measurement;
    }

    private PhMeasurement savePh(Station station, PhWaterType type, Instant measuredAt) {
        PhMeasurement measurement = new PhMeasurement(station, type, new BigDecimal("7.20"), measuredAt, currentUser());
        entityManager.persistAndFlush(measurement);
        return measurement;
    }

    private PhMeasurement saveDeletedPh(Station station, PhWaterType type, Instant measuredAt, AppUser deletedBy) {
        PhMeasurement measurement = savePh(station, type, measuredAt);
        measurement.softDelete(deletedBy);
        entityManager.flush();
        return measurement;
    }

    private LimeRecord saveLime(Station station, LimeRecordType type, Instant recordedAt) {
        LimeRecord record = new LimeRecord(
                station,
                type,
                new BigDecimal("4.00"),
                recordedAt,
                "Test record",
                currentUser()
        );
        entityManager.persistAndFlush(record);
        return record;
    }

    private Stoppage saveStoppage(Station station, Instant startAt) {
        Stoppage stoppage = new Stoppage(station, StoppageReason.FALTA_DE_AGUA, startAt, currentUser());
        entityManager.persistAndFlush(stoppage);
        return stoppage;
    }

    private Stoppage saveDeletedStoppage(Station station, Instant startAt, AppUser deletedBy) {
        Stoppage stoppage = saveStoppage(station, startAt);
        stoppage.softDelete(deletedBy);
        entityManager.flush();
        return stoppage;
    }

    private void saveFinishedStoppage(Station station, Instant startAt) {
        Stoppage stoppage = saveStoppage(station, startAt);
        stoppage.finish(startAt.plusSeconds(60));
        entityManager.flush();
    }

    private AppUser currentUser() {
        return entityManager.getEntityManager()
                .createQuery("select user from AppUser user", AppUser.class)
                .setMaxResults(1)
                .getSingleResult();
    }

    private <T> UUID firstIdDescending(T first, T second, Function<T, UUID> idExtractor) {
        return idsDescending(List.of(first, second), idExtractor).get(0);
    }

    private <T> UUID secondIdDescending(T first, T second, Function<T, UUID> idExtractor) {
        return idsDescending(List.of(first, second), idExtractor).get(1);
    }

    private <T> List<UUID> idsDescending(Collection<T> records, Function<T, UUID> idExtractor) {
        return records.stream()
                .map(idExtractor)
                .sorted(Comparator.comparing(UUID::toString).reversed())
                .toList();
    }

    private record TestContext(
            Station alphaStation,
            Station zuluStation,
            Station extraStation,
            AppUser user
    ) {
    }
}
