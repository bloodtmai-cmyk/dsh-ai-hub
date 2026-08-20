create table conversation_audit (
    id uuid primary key,
    workcode varchar(12) not null,
    session_id varchar(100) not null,
    turn_id varchar(100) not null,
    client_installation_id varchar(100) not null,
    model varchar(120) not null,
    user_message_ciphertext text not null,
    assistant_message_ciphertext text not null,
    started_at timestamp with time zone not null,
    completed_at timestamp with time zone,
    input_tokens bigint not null default 0,
    output_tokens bigint not null default 0,
    latency_ms bigint,
    status varchar(24) not null,
    error_code varchar(80),
    received_at timestamp with time zone not null,
    constraint uq_conversation_audit_turn unique (workcode, session_id, turn_id)
);

create index idx_conversation_audit_workcode_time on conversation_audit (workcode, started_at desc);
create index idx_conversation_audit_status_time on conversation_audit (status, started_at desc);

create table capability (
    id uuid primary key,
    type varchar(16) not null,
    parent_id uuid references capability(id),
    source_kind varchar(24) not null,
    external_ref varchar(240) not null,
    name varchar(120) not null,
    description varchar(1000) not null,
    release_version varchar(80) not null,
    source_ref varchar(500),
    integrity_hash varchar(128),
    status varchar(24) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uq_capability_release unique (type, external_ref, release_version)
);

create index idx_capability_type_status on capability (type, status);
create index idx_capability_parent on capability (parent_id, status);

create table subject_grant (
    id uuid primary key,
    workcode varchar(12) not null,
    capability_id uuid not null references capability(id),
    valid_from timestamp with time zone not null,
    valid_until timestamp with time zone,
    enabled boolean not null,
    created_by varchar(12) not null,
    created_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    constraint uq_subject_grant unique (workcode, capability_id)
);

create index idx_subject_grant_subject on subject_grant (workcode, enabled, valid_from, valid_until);

create table api_key_application (
    id uuid primary key,
    workcode varchar(12) not null,
    purpose varchar(500) not null,
    requested_models varchar(1000) not null,
    status varchar(24) not null,
    submitted_at timestamp with time zone not null,
    reviewed_at timestamp with time zone,
    reviewer varchar(12),
    decision_comment varchar(500),
    version bigint not null default 0
);

create index idx_key_application_subject on api_key_application (workcode, submitted_at desc);
create index idx_key_application_status on api_key_application (status, submitted_at asc);

create table api_key_binding (
    id uuid primary key,
    application_id uuid not null unique references api_key_application(id),
    provider_key_id varchar(200) not null,
    secret_ciphertext text not null,
    secret_hash varchar(64) not null,
    secret_mask varchar(80) not null,
    models varchar(1000) not null,
    quota numeric(12, 2),
    status varchar(24) not null,
    issued_at timestamp with time zone not null,
    expires_at timestamp with time zone,
    claimed_at timestamp with time zone,
    revoked_at timestamp with time zone,
    version bigint not null default 0
);

create table admin_audit_log (
    id uuid primary key,
    actor varchar(40) not null,
    action varchar(80) not null,
    target_type varchar(80) not null,
    target_id varchar(100),
    outcome varchar(24) not null,
    detail varchar(1000),
    occurred_at timestamp with time zone not null
);

create index idx_admin_audit_log_time on admin_audit_log (occurred_at desc);
create index idx_admin_audit_log_target on admin_audit_log (target_type, target_id, occurred_at desc);
