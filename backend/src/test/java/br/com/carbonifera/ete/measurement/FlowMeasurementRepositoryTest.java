package br.com.carbonifera.ete.measurement;

import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class FlowMeasurementRepositoryTest {
    @Autowired FlowMeasurementRepository repository;
    @Autowired org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager entityManager;
    @Test void firstPageHasTen() { assertThat(find(11, 0).getContent()).hasSize(10); }
    @Test void secondPageHasRemainder() { assertThat(find(11, 1).getContent()).hasSize(1); }
    @Test void orderingIsNewestFirst() { assertThat(find(11, 0).getContent()).isSortedAccordingTo((a, b) -> b.getMeasuredAt().compareTo(a.getMeasuredAt())); }

    @Test void acceptsOnlyStartAt() {
        Context c = seed("only-start");
        FlowMeasurement before = save(c.station, c.user, c.start.minusSeconds(1));
        FlowMeasurement after = save(c.station, c.user, c.start.plusSeconds(1));

        assertThat(repository.findForList(List.of(c.station), c.start, null, false, null, PageRequest.of(0, 10)).getContent())
                .extracting(FlowMeasurement::getId)
                .containsExactly(after.getId())
                .doesNotContain(before.getId());
    }

    @Test void acceptsOnlyEndAt() {
        Context c = seed("only-end");
        FlowMeasurement before = save(c.station, c.user, c.start.minusSeconds(1));
        FlowMeasurement after = save(c.station, c.user, c.start.plusSeconds(1));

        assertThat(repository.findForList(List.of(c.station), null, c.start, false, null, PageRequest.of(0, 10)).getContent())
                .extracting(FlowMeasurement::getId)
                .containsExactly(before.getId())
                .doesNotContain(after.getId());
    }

    @Test void persistsLegacyMeasurementWithoutHeight() {
        Context c = seed("legacy-height");
        FlowMeasurement measurement = new FlowMeasurement(
                c.station,
                new BigDecimal("123.45"),
                c.start,
                c.user
        );
        entityManager.persistAndFlush(measurement);
        entityManager.clear();

        FlowMeasurement reloaded = repository.findById(measurement.getId()).orElseThrow();

        assertThat(reloaded.getHeightCm()).isNull();
        assertThat(reloaded.getFlowLiters()).isEqualByComparingTo("123.45");
    }

    @Test void persistsMeasurementHeight() {
        Context c = seed("filled-height");
        FlowMeasurement measurement = new FlowMeasurement(
                c.station,
                new BigDecimal("29.21"),
                c.start,
                c.user
        );
        measurement.setHeightCm(new BigDecimal("12.50"));
        entityManager.persistAndFlush(measurement);
        entityManager.clear();

        FlowMeasurement reloaded = repository.findById(measurement.getId()).orElseThrow();

        assertThat(reloaded.getHeightCm()).isEqualByComparingTo("12.50");
    }

    private org.springframework.data.domain.Page<FlowMeasurement> find(int count, int page) {
        Context c = seed("page-" + count + "-" + page);
        Instant start = c.start;
        for (int i = 0; i < count; i++) save(c.station, c.user, start.plusSeconds(i));
        entityManager.flush();
        return repository.findForList(List.of(c.station), start, start.plusSeconds(100), false, null, PageRequest.of(page, 10));
    }

    private Context seed(String suffix) {
        Station station = entityManager.persistFlushFind(new Station("Station " + suffix, "flow-" + suffix));
        AppUser user = entityManager.persistFlushFind(new AppUser("User " + suffix, "flow-user-" + suffix, "hash", UserRole.ADMIN, false));
        return new Context(station, user, Instant.parse("2026-01-01T00:00:00Z"));
    }

    private FlowMeasurement save(Station station, AppUser user, Instant measuredAt) {
        FlowMeasurement measurement = new FlowMeasurement(station, BigDecimal.ONE, measuredAt, user);
        entityManager.persistAndFlush(measurement);
        return measurement;
    }

    private record Context(Station station, AppUser user, Instant start) {}
}
