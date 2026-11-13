CREATE UNIQUE INDEX IF NOT EXISTS ux_stoppage_station_active
    ON stoppages (station_id)
    WHERE status = 'ACTIVE' AND deleted = false;

ALTER TABLE app_users DROP CONSTRAINT IF EXISTS app_users_role_check;
ALTER TABLE app_users ADD CONSTRAINT app_users_role_check
    CHECK (role IN ('OPERATOR', 'SUPERVISOR', 'ADMIN', 'VIEWER'));

ALTER TABLE flow_measurements ALTER COLUMN created_by_id DROP NOT NULL;
ALTER TABLE ph_measurements ALTER COLUMN created_by_id DROP NOT NULL;

ALTER TABLE audit_events DROP CONSTRAINT IF EXISTS audit_events_record_type_check;
ALTER TABLE audit_events ADD CONSTRAINT audit_events_record_type_check
    CHECK (record_type IN ('PH_MEASUREMENT', 'FLOW_MEASUREMENT', 'STOPPAGE', 'DEVICE_KEY'));

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN (
        'INTERNAL',
        'HOURLY_MEASUREMENT_REMINDER',
        'STOPPAGE_ALERT',
        'CAL_SHIFT_MOVEMENT_REMINDER',
        'LIME_MIN_STOCK_ALERT',
        'OFFLINE_SYNC_FAILURE',
        'DEVICE_OFFLINE_ALERT',
        'DEVICE_BACK_ONLINE'
    ));

-- Preserve the source stream when a human edits the displayed pH type.
ALTER TABLE ph_measurements ADD COLUMN IF NOT EXISTS device_water_type VARCHAR(20);
UPDATE ph_measurements measurement
SET device_water_type = COALESCE(
    (SELECT change.before_value
     FROM audit_events event
     JOIN audit_field_changes change ON change.event_id = event.id
     WHERE event.record_type = 'PH_MEASUREMENT'
       AND event.action = 'UPDATED'
       AND event.record_id = measurement.id
       AND change.name = 'type'
     ORDER BY event.occurred_at, event.id
     LIMIT 1), measurement.type)
WHERE measurement.device_window_end IS NOT NULL AND measurement.device_water_type IS NULL;
ALTER TABLE ph_measurements DROP CONSTRAINT IF EXISTS uk_ph_device_type_window;
ALTER TABLE ph_measurements ADD CONSTRAINT uk_ph_device_type_window
    UNIQUE (device_key_id, device_water_type, device_window_end);
