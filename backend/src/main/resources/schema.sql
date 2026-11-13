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
