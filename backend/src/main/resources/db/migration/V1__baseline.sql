create table app_users (
        active boolean not null,
        change_password boolean not null,
        created_at timestamp(6) with time zone not null,
        updated_at timestamp(6) with time zone not null,
        id uuid not null,
        station_id uuid,
        username varchar(80) not null unique,
        name varchar(120) not null,
        password_hash varchar(255) not null,
        role varchar(255) not null,
        primary key (id)
    );

create table audit_events (
        occurred_at timestamp(6) with time zone not null,
        id uuid not null,
        record_id uuid not null,
        station_id uuid not null,
        user_id uuid not null,
        description varchar(500) not null,
        action varchar(255) not null,
        record_type varchar(255) not null,
        primary key (id)
    );

create table audit_field_changes (
        event_id uuid not null,
        id uuid not null,
        name varchar(80) not null,
        label varchar(120) not null,
        after_value varchar(500),
        before_value varchar(500),
        primary key (id)
    );

create table device_batch_receipts (
        device_key_id uuid not null,
        id uuid not null,
        sample_id varchar(64) not null,
        result varchar(8192) not null,
        primary key (id),
        unique (device_key_id, sample_id)
    );

create table device_keys (
        active boolean not null,
        last_flow_liters numeric(14,2),
        last_height_cm numeric(5,2),
        last_raw_ph numeric(4,2),
        last_treated_ph numeric(4,2),
        created_at timestamp(6) with time zone not null,
        last_flow_at timestamp(6) with time zone,
        last_raw_ph_at timestamp(6) with time zone,
        last_seen_at timestamp(6) with time zone,
        last_treated_ph_at timestamp(6) with time zone,
        offline_notified_for_seen_at timestamp(6) with time zone,
        created_by_id uuid not null,
        id uuid not null,
        station_id uuid not null,
        value_hash varchar(64) not null unique,
        name varchar(120) not null,
        name_key varchar(120) not null,
        encrypted_value varchar(512) not null,
        last_flow_time_estimate_origin varchar(255),
        last_raw_ph_time_estimate_origin varchar(255),
        last_treated_ph_time_estimate_origin varchar(255),
        primary key (id),
        constraint uk_device_key_station_name unique (station_id, name_key)
    );

create table device_readings (
        flow_liters numeric(14,2),
        reading_value numeric(14,2) not null,
        created_at timestamp(6) with time zone not null,
        measured_at timestamp(6) with time zone not null,
        device_key_id uuid not null,
        id uuid not null,
        time_estimate_origin varchar(20),
        type varchar(255) not null,
        primary key (id)
    );

create table flow_measurements (
        deleted boolean not null,
        flow_liters numeric(14,2) not null,
        height_cm numeric(5,2),
        sample_count integer,
        created_at timestamp(6) with time zone not null,
        deleted_at timestamp(6) with time zone,
        device_window_end timestamp(6) with time zone,
        measured_at timestamp(6) with time zone not null,
        client_request_id uuid unique,
        created_by_id uuid,
        deleted_by_id uuid,
        device_key_id uuid,
        id uuid not null,
        station_id uuid not null,
        time_estimate_origin varchar(20),
        device_name varchar(255),
        primary key (id),
        constraint uk_flow_device_window unique (device_key_id, device_window_end)
    );

create table lime_records (
        quantity_bags numeric(12,2) not null,
        created_at timestamp(6) with time zone not null,
        recorded_at timestamp(6) with time zone not null,
        client_request_id uuid unique,
        created_by_id uuid not null,
        id uuid not null,
        station_id uuid not null,
        note varchar(500),
        type varchar(255) not null,
        primary key (id)
    );

create table notifications (
        created_at timestamp(6) with time zone not null,
        read_at timestamp(6) with time zone,
        scheduled_at timestamp(6) with time zone,
        sent_at timestamp(6) with time zone,
        id uuid not null,
        station_id uuid,
        target_user_id uuid,
        external_provider varchar(80),
        dedupe_key varchar(120),
        external_status varchar(120),
        title varchar(140) not null,
        target_url varchar(300),
        message varchar(1000) not null,
        type varchar(255) not null,
        primary key (id),
        constraint uk_notification_dedupe unique (target_user_id, type, dedupe_key)
    );

create table ph_measurements (
        deleted boolean not null,
        ph numeric(4,2) not null,
        sample_count integer,
        created_at timestamp(6) with time zone not null,
        deleted_at timestamp(6) with time zone,
        device_window_end timestamp(6) with time zone,
        measured_at timestamp(6) with time zone not null,
        client_request_id uuid unique,
        created_by_id uuid,
        deleted_by_id uuid,
        device_key_id uuid,
        id uuid not null,
        station_id uuid not null,
        time_estimate_origin varchar(20),
        device_name varchar(255),
        device_water_type varchar(255),
        type varchar(255) not null,
        primary key (id),
        constraint uk_ph_device_type_window unique (device_key_id, device_water_type, device_window_end)
    );

create table push_subscriptions (
        active boolean not null,
        created_at timestamp(6) with time zone not null,
        last_used_at timestamp(6) with time zone not null,
        id uuid not null,
        user_id uuid not null,
        endpoint varchar(500) not null,
        auth varchar(255) not null,
        p256dh varchar(255) not null,
        primary key (id),
        constraint uk_push_subscriptions_endpoint unique (endpoint)
    );

create table stations (
        active boolean not null,
        lime_min_stock_alerted boolean not null,
        min_lime_stock_bags numeric(38,2),
        created_at timestamp(6) with time zone not null,
        id uuid not null,
        slug varchar(80) not null unique,
        name varchar(120) not null unique,
        primary key (id)
    );

create table stoppages (
        deleted boolean not null,
        created_at timestamp(6) with time zone not null,
        deleted_at timestamp(6) with time zone,
        end_at timestamp(6) with time zone,
        start_at timestamp(6) with time zone not null,
        client_request_id uuid unique,
        created_by_id uuid not null,
        deleted_by_id uuid,
        id uuid not null,
        station_id uuid not null,
        reason_detail varchar(200),
        reason varchar(255) not null,
        status varchar(255) not null,
        primary key (id)
    );

create index idx_device_reading_key_type_measured_at 
       on device_readings (device_key_id, type, measured_at);

alter table if exists app_users 
       add constraint FKk7lelhmxj43twrcx99amqen7o 
       foreign key (station_id) 
       references stations;

alter table if exists audit_events 
       add constraint FKm9f0rnqa3p7ftlug222lfgh7d 
       foreign key (station_id) 
       references stations;

alter table if exists audit_events 
       add constraint FKgvcyou34xjx8c0ug9s6hsp9sq 
       foreign key (user_id) 
       references app_users;

alter table if exists audit_field_changes 
       add constraint FKgxeacuqm6dc9ptou5fqn7vh74 
       foreign key (event_id) 
       references audit_events;

alter table if exists device_keys 
       add constraint FKosjhshlguj0shqhb6tjg6ww8x 
       foreign key (created_by_id) 
       references app_users;

alter table if exists device_keys 
       add constraint FKe7qsesd5kav6ub7e8khcewh65 
       foreign key (station_id) 
       references stations;

alter table if exists device_readings 
       add constraint FKduf3731ymwjcdtcvjnyd9ytwp 
       foreign key (device_key_id) 
       references device_keys;

alter table if exists flow_measurements 
       add constraint FK3q321kwn9bhmx85kuhgg2ljju 
       foreign key (created_by_id) 
       references app_users;

alter table if exists flow_measurements 
       add constraint FKouxpkpbswwgod6mkew0hcpqie 
       foreign key (deleted_by_id) 
       references app_users;

alter table if exists flow_measurements 
       add constraint FKgynel1s9yqhylb61wbt60821k 
       foreign key (device_key_id) 
       references device_keys;

alter table if exists flow_measurements 
       add constraint FKm879wcag4aqjdledbtu4tb770 
       foreign key (station_id) 
       references stations;

alter table if exists lime_records 
       add constraint FK98vy5dsu3f9kfdvn6ogbpj73h 
       foreign key (created_by_id) 
       references app_users;

alter table if exists lime_records 
       add constraint FK51t1s8dwlevina5m7sah304jh 
       foreign key (station_id) 
       references stations;

alter table if exists notifications 
       add constraint FKrtrrbgacpo5bk9bb8sbdc4ulp 
       foreign key (station_id) 
       references stations;

alter table if exists notifications 
       add constraint FKoqw1rdyx07eqryd8vw6n1np6d 
       foreign key (target_user_id) 
       references app_users;

alter table if exists ph_measurements 
       add constraint FK184hsgo925dw52titsp7e66bg 
       foreign key (created_by_id) 
       references app_users;

alter table if exists ph_measurements 
       add constraint FKa3dfrrocrsbnpsec29avr5fsw 
       foreign key (deleted_by_id) 
       references app_users;

alter table if exists ph_measurements 
       add constraint FKbl6q0ktxfqo1rmb5mjjpir4j7 
       foreign key (device_key_id) 
       references device_keys;

alter table if exists ph_measurements 
       add constraint FK8onopc6mih23j7lpf1otddnb7 
       foreign key (station_id) 
       references stations;

alter table if exists push_subscriptions 
       add constraint FK2h3egdromkv6qk46cuul9jol6 
       foreign key (user_id) 
       references app_users;

alter table if exists stoppages 
       add constraint FKgyv2aa4c8ik7imkvnwlicgxll 
       foreign key (created_by_id) 
       references app_users;

alter table if exists stoppages 
       add constraint FKhk8s5li0y9t6rirgb0hb8ottf 
       foreign key (deleted_by_id) 
       references app_users;

alter table if exists stoppages 
       add constraint FK5fpcn0idafblhh87r4vnlvex9 
       foreign key (station_id) 
       references stations;

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
