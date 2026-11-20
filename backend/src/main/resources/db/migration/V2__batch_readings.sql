CREATE TABLE IF NOT EXISTS device_batch_receipts (
    id UUID PRIMARY KEY,
    device_key_id UUID NOT NULL,
    sample_id VARCHAR(64) NOT NULL,
    result VARCHAR(8192) NOT NULL,
    UNIQUE (device_key_id, sample_id)
);
ALTER TABLE device_readings ADD COLUMN IF NOT EXISTS time_estimate_origin VARCHAR(20);
ALTER TABLE flow_measurements ADD COLUMN IF NOT EXISTS time_estimate_origin VARCHAR(20);
ALTER TABLE ph_measurements ADD COLUMN IF NOT EXISTS time_estimate_origin VARCHAR(20);
ALTER TABLE device_keys ADD COLUMN IF NOT EXISTS last_flow_time_estimate_origin VARCHAR(255);
ALTER TABLE device_keys ADD COLUMN IF NOT EXISTS last_raw_ph_time_estimate_origin VARCHAR(255);
ALTER TABLE device_keys ADD COLUMN IF NOT EXISTS last_treated_ph_time_estimate_origin VARCHAR(255);
