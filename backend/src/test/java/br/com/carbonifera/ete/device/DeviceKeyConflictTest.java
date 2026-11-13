package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.common.error.BusinessException;
import br.com.carbonifera.ete.config.DeviceProperties;
import br.com.carbonifera.ete.security.CurrentUserService;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.station.StationService;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.sql.SQLException;
import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceKeyConflictTest {
    private final UUID stationId = UUID.randomUUID();
    private final DeviceKeyRepository repository = mock(DeviceKeyRepository.class);
    private DeviceKeyService service;

    @BeforeEach
    void setUp() {
        StationService stations = mock(StationService.class);
        when(stations.requireStation(stationId)).thenReturn(new Station("ETE", "ete"));
        when(repository.save(any(DeviceKey.class))).thenAnswer(invocation -> invocation.getArgument(0));
        DeviceProperties properties = new DeviceProperties("test-secret-which-is-at-least-32-chars", 5, 10);
        service = new DeviceKeyService(repository, new DeviceKeyGenerator(), new DeviceKeyCipher(properties),
                new DeviceKeyHasher(), stations, mock(CurrentUserService.class), properties, Clock.systemUTC());
    }

    @Test
    void translatesPostgresStationNameUniqueViolationFromFlushToConflict() {
        failFlush("uk_device_key_station_name", "23505");

        assertThatThrownBy(() -> service.create("Sensor", stationId, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void preservesDifferentConstraintWithSimilarName() {
        DataIntegrityViolationException original = failFlush("uk_device_key_station_name_other", "23505");

        assertThatThrownBy(() -> service.create("Sensor", stationId, null)).isSameAs(original);
    }

    @Test
    void preservesNonUniqueViolationEvenWhenConstraintNameMatches() {
        DataIntegrityViolationException original = failFlush("uk_device_key_station_name", "23503");

        assertThatThrownBy(() -> service.create("Sensor", stationId, null)).isSameAs(original);
    }

    private DataIntegrityViolationException failFlush(String constraint, String sqlState) {
        var original = new DataIntegrityViolationException("database constraint",
                new ConstraintViolationException("constraint", new SQLException("constraint", sqlState), constraint));
        doThrow(original).when(repository).flush();
        return original;
    }
}
