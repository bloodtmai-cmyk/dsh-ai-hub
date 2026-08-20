create table instruction_artifact (
    id uuid primary key,
    capability_id uuid not null unique references capability(id) on delete cascade,
    file_name varchar(180) not null,
    media_type varchar(120) not null,
    size_bytes bigint not null,
    sha256 varchar(64) not null,
    content bytea not null,
    uploaded_by varchar(40) not null,
    uploaded_at timestamp with time zone not null
);
