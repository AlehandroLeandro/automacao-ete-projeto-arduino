package br.com.carbonifera.ete.stoppage;

import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class StoppageRepositoryTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-02-01T00:00:00Z");

    @Autowired StoppageRepository repository;
    @Autowired TestEntityManager entityManager;

    @Test void filtersByAuthor() {
        Context c = context("author");
        Stoppage own = save(c.station, StoppageReason.FALTA_DE_AGUA, START.plusSeconds(1), c.author);
        save(c.station, StoppageReason.FALTA_DE_AGUA, START.plusSeconds(2), c.other);
        assertThat(find(c, c.author.getId(), null).getContent()).extracting(Stoppage::getId).containsExactly(own.getId());
    }

    @Test void filtersByReason() {
        Context c = context("reason");
        Stoppage wanted = save(c.station, StoppageReason.FALTA_DE_AGUA, START.plusSeconds(1), c.author);
        save(c.station, StoppageReason.FALTA_DE_CAL, START.plusSeconds(2), c.author);
        assertThat(find(c, null, StoppageReason.FALTA_DE_AGUA).getContent()).extracting(Stoppage::getId).containsExactly(wanted.getId());
    }

    @Test void combinesAuthorAndReasonWithAnd() {
        Context c = context("combination");
        Stoppage wanted = save(c.station, StoppageReason.FALTA_DE_AGUA, START.plusSeconds(1), c.author);
        save(c.station, StoppageReason.FALTA_DE_CAL, START.plusSeconds(2), c.author);
        save(c.station, StoppageReason.FALTA_DE_AGUA, START.plusSeconds(3), c.other);
        assertThat(find(c, c.author.getId(), StoppageReason.FALTA_DE_AGUA).getContent()).extracting(Stoppage::getId).containsExactly(wanted.getId());
    }

    @Test void returnsEmptyPageWhenNoRowsMatch() {
        Context c = context("empty");
        assertThat(find(c, null, StoppageReason.OUTROS).getContent()).isEmpty();
    }

    @Test void preservesDescendingStartAndIdOrdering() {
        Context c = context("ordering");
        Stoppage older = save(c.station, StoppageReason.OUTROS, START.plusSeconds(1), c.author);
        Stoppage newer = save(c.station, StoppageReason.OUTROS, START.plusSeconds(2), c.author);
        Page<Stoppage> page = find(c, null, null);
        assertThat(page.getContent()).extracting(Stoppage::getId).containsExactly(newer.getId(), older.getId());
    }

    @Test void acceptsOnlyStartAt() {
        Context c = context("only-start");
        Stoppage beforeClosed = save(c.station, StoppageReason.OUTROS, START.minusSeconds(10), c.author);
        beforeClosed.finish(START.minusSeconds(5));
        Stoppage after = save(c.station, StoppageReason.OUTROS, START.plusSeconds(1), c.author);
        entityManager.flush();

        assertThat(repository.findForList(List.of(c.station), null, START, null, false, null, null, PageRequest.of(0, 10)).getContent())
                .extracting(Stoppage::getId)
                .containsExactly(after.getId())
                .doesNotContain(beforeClosed.getId());
    }

    @Test void acceptsOnlyEndAt() {
        Context c = context("only-end");
        Stoppage before = save(c.station, StoppageReason.OUTROS, START.minusSeconds(1), c.author);
        Stoppage after = save(c.station, StoppageReason.OUTROS, END.plusSeconds(1), c.author);

        assertThat(repository.findForList(List.of(c.station), null, null, END, false, null, null, PageRequest.of(0, 10)).getContent())
                .extracting(Stoppage::getId)
                .containsExactly(before.getId())
                .doesNotContain(after.getId());
    }

    private Page<Stoppage> find(Context c, java.util.UUID authorId, StoppageReason reason) {
        return repository.findForList(List.of(c.station), StoppageStatus.ACTIVE, START, END, false, authorId, reason, PageRequest.of(0, 10));
    }

    private Context context(String suffix) {
        Station station = entityManager.persistFlushFind(new Station("Station " + suffix, "stoppage-" + suffix));
        AppUser author = entityManager.persistFlushFind(new AppUser("Author " + suffix, "author-" + suffix, "hash", UserRole.ADMIN, false));
        AppUser other = entityManager.persistFlushFind(new AppUser("Other " + suffix, "other-" + suffix, "hash", UserRole.ADMIN, false));
        return new Context(station, author, other);
    }

    private Stoppage save(Station station, StoppageReason reason, Instant startAt, AppUser author) {
        Stoppage stoppage = new Stoppage(station, reason, startAt, author);
        entityManager.persistAndFlush(stoppage);
        return stoppage;
    }

    private record Context(Station station, AppUser author, AppUser other) {}
}
