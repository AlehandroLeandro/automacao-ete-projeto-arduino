package br.com.ete.measurement;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhDeviceWindowSchemaTest {

    @Test
    void migratesLegacyWindowIdentityAndReplacesMutableTypeUniquenessIdempotently() throws Exception {
        String schema = new ClassPathResource("schema.sql").getContentAsString(StandardCharsets.UTF_8);
        String migration = schema.substring(schema.indexOf("-- Preserve the source stream"));
        var script = new ByteArrayResource(migration.getBytes(StandardCharsets.UTF_8));
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL")) {
            var sql = connection.createStatement();
            sql.execute("""
                    CREATE TABLE ph_measurements (id INT PRIMARY KEY, device_key_id INT, type VARCHAR(20),
                        device_window_end TIMESTAMP WITH TIME ZONE,
                        CONSTRAINT uk_ph_device_type_window UNIQUE(device_key_id, type, device_window_end))
                    """);
            sql.execute("""
                    CREATE TABLE audit_events (id INT PRIMARY KEY, record_id INT, record_type VARCHAR(40),
                        action VARCHAR(20), occurred_at TIMESTAMP WITH TIME ZONE)
                    """);
            sql.execute("CREATE TABLE audit_field_changes (event_id INT, name VARCHAR(80), before_value VARCHAR(500))");
            sql.execute("INSERT INTO ph_measurements VALUES (1, 10, 'TREATED', '2026-11-13 18:00:00Z')");
            sql.execute("INSERT INTO ph_measurements VALUES (2, 10, 'TREATED', '2026-11-13 19:00:00Z')");
            sql.execute("INSERT INTO ph_measurements VALUES (3, NULL, 'RAW', NULL)");
            sql.execute("INSERT INTO audit_events VALUES (1, 1, 'PH_MEASUREMENT', 'UPDATED', '2026-11-13 18:01:00Z')");
            sql.execute("INSERT INTO audit_events VALUES (2, 1, 'PH_MEASUREMENT', 'UPDATED', '2026-11-13 18:02:00Z')");
            sql.execute("INSERT INTO audit_field_changes VALUES (1, 'type', 'RAW'), (2, 'type', 'TREATED')");

            ScriptUtils.executeSqlScript(connection, script);

            var rows = sql.executeQuery("SELECT device_water_type FROM ph_measurements ORDER BY id");
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("RAW");
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("TREATED");
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isNull();
            // The original RAW and TREATED streams may now share the displayed type and window.
            sql.execute("INSERT INTO ph_measurements VALUES (4, 10, 'TREATED', '2026-11-13 18:00:00Z', 'TREATED')");
            sql.execute("UPDATE ph_measurements SET type = 'RAW' WHERE id = 4");
            ScriptUtils.executeSqlScript(connection, script);
            rows = sql.executeQuery("SELECT device_water_type FROM ph_measurements WHERE id = 4");
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("TREATED");
            assertThatThrownBy(() -> sql.execute("""
                    INSERT INTO ph_measurements VALUES (5, 10, 'RAW', '2026-11-13 18:00:00Z', 'RAW')
                    """)).isInstanceOf(java.sql.SQLException.class);
        }
    }
}
