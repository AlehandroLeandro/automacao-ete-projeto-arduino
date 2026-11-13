package br.com.carbonifera.ete.lime;

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
class LimeRecordRepositoryTest {
    @Autowired LimeRecordRepository repository;
    @Autowired org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager entityManager;
    @Test void firstPageHasTen() { assertThat(find(11, 0).getContent()).hasSize(10); }
    @Test void secondPageHasRemainder() { assertThat(find(11, 1).getContent()).hasSize(1); }
    @Test void orderingIsNewestFirst() { assertThat(find(11, 0).getContent()).isSortedAccordingTo((a, b) -> b.getRecordedAt().compareTo(a.getRecordedAt())); }

    @Test void acceptsOnlyStartAt() {
        Context c = seed("only-start");
        LimeRecord before = save(c.station, c.user, c.start.minusSeconds(1));
        LimeRecord after = save(c.station, c.user, c.start.plusSeconds(1));

        assertThat(repository.findForList(List.of(c.station), LimeRecordType.ENTRY, c.start, null, null, PageRequest.of(0, 10)).getContent())
                .extracting(LimeRecord::getId)
                .containsExactly(after.getId())
                .doesNotContain(before.getId());
    }

    @Test void acceptsOnlyEndAt() {
        Context c = seed("only-end");
        LimeRecord before = save(c.station, c.user, c.start.minusSeconds(1));
        LimeRecord after = save(c.station, c.user, c.start.plusSeconds(1));

        assertThat(repository.findForList(List.of(c.station), LimeRecordType.ENTRY, null, c.start, null, PageRequest.of(0, 10)).getContent())
                .extracting(LimeRecord::getId)
                .containsExactly(before.getId())
                .doesNotContain(after.getId());
    }

    private org.springframework.data.domain.Page<LimeRecord> find(int count, int page) {
        Context c = seed("page-" + count + "-" + page);
        Instant start = c.start;
        for (int i = 0; i < count; i++) save(c.station, c.user, start.plusSeconds(i));
        entityManager.flush();
        return repository.findForList(List.of(c.station), LimeRecordType.ENTRY, start, start.plusSeconds(100), null, PageRequest.of(page, 10));
    }

    private Context seed(String suffix) {
        Station station = entityManager.persistFlushFind(new Station("Station " + suffix, "lime-" + suffix));
        AppUser user = entityManager.persistFlushFind(new AppUser("User " + suffix, "lime-user-" + suffix, "hash", UserRole.ADMIN, false));
        return new Context(station, user, Instant.parse("2026-01-01T00:00:00Z"));
    }

    private LimeRecord save(Station station, AppUser user, Instant recordedAt) {
        LimeRecord record = new LimeRecord(station, LimeRecordType.ENTRY, BigDecimal.ONE, recordedAt, null, user);
        entityManager.persistAndFlush(record);
        return record;
    }

    private record Context(Station station, AppUser user, Instant start) {}
}
