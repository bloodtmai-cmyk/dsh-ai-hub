create table desktop_release (
    id uuid primary key,
    version varchar(80) not null,
    platform varchar(24) not null,
    status varchar(24) not null,
    published_slot boolean,
    file_name varchar(180) not null,
    media_type varchar(120) not null,
    size_bytes bigint not null,
    sha256 varchar(64) not null,
    artifact_key varchar(100) not null unique,
    release_notes text not null,
    created_by varchar(40) not null,
    created_at timestamp with time zone not null,
    published_by varchar(40),
    published_at timestamp with time zone,
    superseded_at timestamp with time zone,
    constraint uq_desktop_release_platform_version unique (platform, version),
    constraint uq_desktop_release_published_platform unique (platform, published_slot),
    constraint ck_desktop_release_published_slot check (
        (status = 'PUBLISHED' and published_slot = true)
        or (status <> 'PUBLISHED' and published_slot is null)
    )
);

create index idx_desktop_release_platform_status
    on desktop_release (platform, status, published_at desc);
