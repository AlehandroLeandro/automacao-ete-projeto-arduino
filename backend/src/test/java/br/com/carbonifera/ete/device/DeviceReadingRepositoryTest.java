package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:device-reading-repository-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class DeviceReadingRepositoryTest {

    private static final Instant HOUR_START = Instant.parse("2026-01-01T14:00:00Z");

    @Autowired DeviceReadingRepository repository;
    @Autowired org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager entityManager;

    @Test
    void findsOnlyReadingsMeasuredBeforeHourStart() {
        Context context = seed("pending");
        DeviceReading before = save(context.key, DeviceReadingType.FLOW, HOUR_START.minusSeconds(1));
        save(context.key, DeviceReadingType.FLOW, HOUR_START);
        save(context.key, DeviceReadingType.FLOW, HOUR_START.plusSeconds(1));

        assertThat(repository.findPendingBefore(HOUR_START))
                .extracting(DeviceReading::getId)
                .containsExactly(before.getId());
    }

    @Test
    void findsTypeWithinStartInclusiveEndExclusiveInterval() {
        Context context = seed("interval");
        DeviceReading atStart = save(context.key, DeviceReadingType.FLOW, HOUR_START);
        DeviceReading beforeEnd = save(context.key, DeviceReadingType.FLOW, HOUR_START.plusSeconds(3599));
        save(context.key, DeviceReadingType.FLOW, HOUR_START.plusSeconds(3600));
        save(context.key, DeviceReadingType.PH_RAW, HOUR_START.plusSeconds(1));

        assertThat(repository.findByDeviceKeyAndTypeAndMeasuredAtBetween(
                context.key, DeviceReadingType.FLOW, HOUR_START, HOUR_START.plusSeconds(3600)))
                .extracting(DeviceReading::getId)
                .containsExactly(atStart.getId(), beforeEnd.getId());
    }

    @Test
    void deletesOnlyReadingsForTheRequestedDeviceKey() {
        Context context = seed("delete-key");
        Context other = seed("delete-other");
        DeviceReading target = save(context.key, DeviceReadingType.FLOW, HOUR_START);
        DeviceReading otherReading = save(other.key, DeviceReadingType.FLOW, HOUR_START);

        assertThat(repository.deleteByDeviceKey(context.key)).isEqualTo(1);
        entityManager.clear();

        assertThat(repository.findAll()).extracting(DeviceReading::getId).containsExactly(otherReading.getId());
        assertThat(repository.findById(target.getId())).isEmpty();
    }

    @Test
    void deletesOnlyReadingsWithRequestedIds() {
        Context context = seed("delete-ids");
        DeviceReading deleted = save(context.key, DeviceReadingType.FLOW, HOUR_START);
        DeviceReading retained = save(context.key, DeviceReadingType.PH_RAW, HOUR_START.plusSeconds(1));

        assertThat(repository.deleteAllByIdIn(List.of(deleted.getId()))).isEqualTo(1);
        entityManager.clear();

        assertThat(repository.findAll()).extracting(DeviceReading::getId).containsExactly(retained.getId());
    }

    private DeviceReading save(DeviceKey key, DeviceReadingType type, Instant measuredAt) {
        DeviceReading reading = new DeviceReading(key, type, new BigDecimal("1.25"), null, measuredAt);
        entityManager.persistAndFlush(reading);
        return reading;
    }

    private Context seed(String suffix) {
        Station station = entityManager.persistFlushFind(new Station("Station " + suffix, "device-reading-" + suffix));
        AppUser user = entityManager.persistFlushFind(new AppUser(
                "Admin " + suffix, "device-reading-user-" + suffix, "hash", UserRole.ADMIN, false));
        DeviceKey key = entityManager.persistFlushFind(new DeviceKey(
                station, "Sensor " + suffix, "encrypted", "device-reading-hash-" + suffix, user));
        return new Context(key);
    }

    private record Context(DeviceKey key) {}
}
