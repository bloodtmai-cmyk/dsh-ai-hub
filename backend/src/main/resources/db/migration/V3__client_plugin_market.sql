create table client_plugin_artifact (
    id uuid primary key,
    capability_id uuid not null unique references capability(id) on delete cascade,
    file_name varchar(180) not null,
    media_type varchar(120) not null,
    size_bytes bigint not null,
    sha256 varchar(64) not null,
    manifest_json text not null,
    content bytea not null,
    uploaded_by varchar(40) not null,
    uploaded_at timestamp with time zone not null
);

create table capability_application (
    id uuid primary key,
    workcode varchar(12) not null,
    capability_id uuid not null references capability(id),
    reason varchar(500) not null,
    status varchar(24) not null,
    submitted_at timestamp with time zone not null,
    reviewed_at timestamp with time zone,
    reviewer varchar(40),
    decision_comment varchar(500),
    version bigint not null default 0
);

create index idx_capability_application_subject on capability_application (workcode, submitted_at desc);
create index idx_capability_application_status on capability_application (status, submitted_at asc);
create index idx_capability_application_capability on capability_application (capability_id, submitted_at desc);
