package br.com.carbonifera.ete.measurement;

import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class PhMeasurementRepositoryTest {
    @Autowired PhMeasurementRepository repository;
    @Autowired org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager entityManager;

    @Test void firstPageContainsTenRows() {
        Context context = seed(11);
        Page<PhMeasurement> page = find(context, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(10);
    }

    @Test void secondPageContainsRemainder() {
        Context context = seed(11);
        Page<PhMeasurement> page = find(context, PageRequest.of(1, 10));
        assertThat(page.getContent()).hasSize(1);
    }

    @Test void pagesKeepNewestFirstOrdering() {
        Context context = seed(11);
        Page<PhMeasurement> page = find(context, PageRequest.of(0, 10));
        assertThat(page.getContent()).isSortedAccordingTo((a, b) -> b.getMeasuredAt().compareTo(a.getMeasuredAt()));
    }

    @Test void acceptsOnlyStartAt() {
        Context c = seed("only-start");
        PhMeasurement before = save(c.station, c.user, c.start.minusSeconds(1));
        PhMeasurement after = save(c.station, c.user, c.start.plusSeconds(1));

        assertThat(repository.findForList(List.of(c.station), PhWaterType.RAW, c.start, null, false, null, PageRequest.of(0, 10)).getContent())
                .extracting(PhMeasurement::getId)
                .containsExactly(after.getId())
                .doesNotContain(before.getId());
    }

    @Test void acceptsOnlyEndAt() {
        Context c = seed("only-end");
        PhMeasurement before = save(c.station, c.user, c.start.minusSeconds(1));
        PhMeasurement after = save(c.station, c.user, c.start.plusSeconds(1));

        assertThat(repository.findForList(List.of(c.station), PhWaterType.RAW, null, c.start, false, null, PageRequest.of(0, 10)).getContent())
                .extracting(PhMeasurement::getId)
                .containsExactly(before.getId())
                .doesNotContain(after.getId());
    }

    private Page<PhMeasurement> find(Context context, PageRequest request) {
        return repository.findForList(List.of(context.station), PhWaterType.RAW,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-02-01T00:00:00Z"), false, null, request);
    }

    private Context seed(int count) {
        Context c = seed("page-" + count);
        for (int i = 0; i < count; i++) save(c.station, c.user, c.start.plusSeconds(i));
        entityManager.flush();
        return c;
    }

    private Context seed(String suffix) {
        Station station = entityManager.persistFlushFind(new Station("Station " + suffix, "station-" + suffix));
        AppUser user = entityManager.persistFlushFind(new AppUser("User " + suffix, "user-" + suffix, "hash", UserRole.ADMIN, false));
        return new Context(station, user, Instant.parse("2026-01-01T00:00:00Z"));
    }

    private PhMeasurement save(Station station, AppUser user, Instant measuredAt) {
        PhMeasurement measurement = new PhMeasurement(station, PhWaterType.RAW, BigDecimal.valueOf(7), measuredAt, user);
        entityManager.persistAndFlush(measurement);
        return measurement;
    }

    private record Context(Station station, AppUser user, Instant start) {}
}
