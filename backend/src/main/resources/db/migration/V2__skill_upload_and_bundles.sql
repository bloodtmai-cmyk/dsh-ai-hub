create table skill_artifact (
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

create table bundle_member (
    id uuid primary key,
    bundle_id uuid not null references capability(id) on delete cascade,
    member_id uuid not null references capability(id),
    required boolean not null,
    sort_order integer not null,
    constraint uq_bundle_member unique (bundle_id, member_id),
    constraint ck_bundle_not_self check (bundle_id <> member_id)
);

create index idx_bundle_member_bundle on bundle_member (bundle_id, sort_order);
create index idx_bundle_member_member on bundle_member (member_id);
