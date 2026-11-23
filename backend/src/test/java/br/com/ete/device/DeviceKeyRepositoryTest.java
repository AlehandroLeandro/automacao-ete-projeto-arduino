package br.com.ete.device;

import br.com.ete.station.Station;
import br.com.ete.user.AppUser;
import br.com.ete.user.UserRole;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class DeviceKeyRepositoryTest {

    @Autowired DeviceKeyRepository repository;
    @Autowired org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager entityManager;

    @Test
    void findsKeyByHashAndReturnsEmptyForUnknownHash() {
        Context context = seed("hash");
        DeviceKey key = save(context, "sensor-hash", "Sensor");

        assertThat(repository.findByValueHash("sensor-hash")).contains(key);
        assertThat(repository.findByValueHash("unknown-hash")).isEmpty();
    }

    @Test
    void checksNameKeyWithinTheSameStationOnly() {
        Context first = seed("exists-first");
        Context second = seed("exists-second");
        save(first, "exists-hash", "Sensor");

        assertThat(repository.existsByStationAndNameKey(first.station, "sensor")).isTrue();
        assertThat(repository.existsByStationAndNameKey(second.station, "sensor")).isFalse();
        assertThat(repository.existsByStationAndNameKey(first.station, "other")).isFalse();
    }

    @Test
    void rejectsDuplicateNameKeyForTheSameStation() {
        Context context = seed("duplicate");
        save(context, "duplicate-hash-1", "Sensor");
        DeviceKey duplicate = new DeviceKey(context.station, "sensor", "encrypted", "duplicate-hash-2", context.user);

        assertThatThrownBy(() -> entityManager.persistAndFlush(duplicate))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void listsActiveKeysOnlyForRequestedStations() {
        Context first = seed("stations-first");
        Context second = seed("stations-second");
        Context other = seed("stations-other");
        DeviceKey inFirst = save(first, "stations-hash-1", "First");
        DeviceKey inSecond = save(second, "stations-hash-2", "Second");
        save(other, "stations-hash-3", "Other");

        assertThat(repository.findAllByStationInAndActiveTrue(List.of(first.station, second.station)))
                .extracting(DeviceKey::getId)
                .containsExactlyInAnyOrder(inFirst.getId(), inSecond.getId());
    }

    private DeviceKey save(Context context, String hash, String name) {
        DeviceKey key = new DeviceKey(context.station, name, "encrypted", hash, context.user);
        entityManager.persistAndFlush(key);
        return key;
    }

    private Context seed(String suffix) {
        Station station = entityManager.persistFlushFind(new Station("Station " + suffix, "device-key-" + suffix));
        AppUser user = entityManager.persistFlushFind(new AppUser(
                "Admin " + suffix, "device-key-user-" + suffix, "hash", UserRole.ADMIN, false));
        return new Context(station, user);
    }

    private record Context(Station station, AppUser user) {}
}
