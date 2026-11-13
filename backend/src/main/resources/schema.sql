CREATE UNIQUE INDEX IF NOT EXISTS ux_stoppage_station_active
    ON stoppages (station_id)
    WHERE status = 'ACTIVE' AND deleted = false;

ALTER TABLE app_users DROP CONSTRAINT IF EXISTS app_users_role_check;
ALTER TABLE app_users ADD CONSTRAINT app_users_role_check
    CHECK (role IN ('OPERATOR', 'SUPERVISOR', 'ADMIN', 'VIEWER'));
