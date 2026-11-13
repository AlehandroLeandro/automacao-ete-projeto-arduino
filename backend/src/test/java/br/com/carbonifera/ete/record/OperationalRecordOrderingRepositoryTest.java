package br.com.carbonifera.ete.record;

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
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class OperationalRecordOrderingRepositoryTest {

    private static final Instant START_AT = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant END_AT = Instant.parse("2026-07-02T00:00:00Z");
    private static final Instant OLDER = Instant.parse("2026-07-01T08:00:00Z");
    private static final Instant TIED = Instant.parse("2026-07-01T09:00:00Z");
    private static final Instant NEWER = Instant.parse("2026-07-01T10:00:00Z");

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private FlowMeasurementRepository flowMeasurementRepository;

    @Autowired
    private PhMeasurementRepository phMeasurementRepository;

    @Autowired
    private LimeRecordRepository limeRecordRepository;

    @Autowired
    private StoppageRepository stoppageRepository;

    @Test
    void flowMeasurementsAreOrderedByMeasuredAtDescThenIdDescAcrossStations() {
        TestData testData = testData("flow");
        FlowMeasurement older = persist(new FlowMeasurement(testData.alphaStation(), new BigDecimal("1000.00"), OLDER, testData.user()));
        FlowMeasurement tiedFirst = persist(new FlowMeasurement(testData.middleStation(), new BigDecimal("1100.00"), TIED, testData.user()));
        FlowMeasurement tiedSecond = persist(new FlowMeasurement(testData.middleStation(), new BigDecimal("1200.00"), TIED, testData.user()));
        FlowMeasurement newer = persist(new FlowMeasurement(testData.zuluStation(), new BigDecimal("1300.00"), NEWER, testData.user()));
        flushAndClear();

        List<FlowMeasurement> records = flowMeasurementRepository.findForList(
                testData.stations(),
                START_AT,
                END_AT,
                true,
                null
        );

        assertThat(records).extracting(FlowMeasurement::getId)
                .containsExactlyElementsOf(expectedIds(newer, tiedFirst, tiedSecond, older));
    }

    @Test
    void phMeasurementsAreOrderedByMeasuredAtDescThenIdDescAcrossStations() {
        TestData testData = testData("ph");
        PhMeasurement older = persist(new PhMeasurement(testData.alphaStation(), PhWaterType.RAW, new BigDecimal("7.10"), OLDER, testData.user()));
        PhMeasurement tiedFirst = persist(new PhMeasurement(testData.middleStation(), PhWaterType.RAW, new BigDecimal("7.20"), TIED, testData.user()));
        PhMeasurement tiedSecond = persist(new PhMeasurement(testData.middleStation(), PhWaterType.RAW, new BigDecimal("7.30"), TIED, testData.user()));
        PhMeasurement newer = persist(new PhMeasurement(testData.zuluStation(), PhWaterType.RAW, new BigDecimal("7.40"), NEWER, testData.user()));
        flushAndClear();

        List<PhMeasurement> records = phMeasurementRepository.findForList(
                testData.stations(),
                null,
                START_AT,
                END_AT,
                true,
                null
        );

        assertThat(records).extracting(PhMeasurement::getId)
                .containsExactlyElementsOf(expectedIds(newer, tiedFirst, tiedSecond, older));
    }

    @Test
    void limeRecordsAreOrderedByRecordedAtDescThenIdDescAcrossStations() {
        TestData testData = testData("lime");
        LimeRecord older = persist(new LimeRecord(testData.alphaStation(), LimeRecordType.ENTRY, new BigDecimal("10.00"), OLDER, null, testData.user()));
        LimeRecord tiedFirst = persist(new LimeRecord(testData.middleStation(), LimeRecordType.ENTRY, new BigDecimal("20.00"), TIED, null, testData.user()));
        LimeRecord tiedSecond = persist(new LimeRecord(testData.middleStation(), LimeRecordType.ENTRY, new BigDecimal("30.00"), TIED, null, testData.user()));
        LimeRecord newer = persist(new LimeRecord(testData.zuluStation(), LimeRecordType.ENTRY, new BigDecimal("40.00"), NEWER, null, testData.user()));
        flushAndClear();

        List<LimeRecord> records = limeRecordRepository.findForList(
                testData.stations(),
                null,
                START_AT,
                END_AT,
                null
        );

        assertThat(records).extracting(LimeRecord::getId)
                .containsExactlyElementsOf(expectedIds(newer, tiedFirst, tiedSecond, older));
    }

    @Test
    void stoppagesAreOrderedByStartAtDescThenIdDescAcrossStations() {
        TestData testData = testData("stoppage");
        Stoppage older = persist(new Stoppage(testData.alphaStation(), StoppageReason.OUTROS, OLDER, testData.user()));
        Stoppage tiedFirst = persist(new Stoppage(testData.middleStation(), StoppageReason.OUTROS, TIED, testData.user()));
        Stoppage tiedSecond = persist(new Stoppage(testData.middleStation(), StoppageReason.OUTROS, TIED, testData.user()));
        Stoppage newer = persist(new Stoppage(testData.zuluStation(), StoppageReason.OUTROS, NEWER, testData.user()));
        flushAndClear();

        List<Stoppage> records = stoppageRepository.findForList(
                testData.stations(),
                null,
                START_AT,
                END_AT,
                true
        );

        assertThat(records).extracting(Stoppage::getId)
                .containsExactlyElementsOf(expectedIds(newer, tiedFirst, tiedSecond, older));
    }

    private TestData testData(String suffix) {
        Station alphaStation = persist(new Station("A ETE " + suffix, "a-ete-" + suffix));
        Station middleStation = persist(new Station("M ETE " + suffix, "m-ete-" + suffix));
        Station zuluStation = persist(new Station("Z ETE " + suffix, "z-ete-" + suffix));
        AppUser user = persist(new AppUser("Operator " + suffix, "operator-" + suffix, "hash", UserRole.OPERATOR, alphaStation, false));
        return new TestData(alphaStation, middleStation, zuluStation, user);
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private <T> List<UUID> expectedIds(T newest, T firstTie, T secondTie, T oldest) {
        return List.of(
                idOf(newest),
                sortedByIdDesc(firstTie, secondTie).get(0),
                sortedByIdDesc(firstTie, secondTie).get(1),
                idOf(oldest)
        );
    }

    private <T> List<UUID> sortedByIdDesc(T first, T second) {
        return List.of(idOf(first), idOf(second)).stream()
                .sorted(Comparator.comparing(UUID::toString).reversed())
                .toList();
    }

    private UUID idOf(Object entity) {
        return (UUID) entityManager.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier(entity);
    }

    private record TestData(Station alphaStation, Station middleStation, Station zuluStation, AppUser user) {

        private List<Station> stations() {
            return List.of(alphaStation, middleStation, zuluStation);
        }
    }
}
