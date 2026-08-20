create table subject_tool_exclusion (
    id uuid primary key,
    workcode varchar(12) not null,
    mcp_capability_id uuid not null references capability(id),
    tool_capability_id uuid not null references capability(id),
    enabled boolean not null,
    created_by varchar(40) not null,
    created_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    constraint uq_subject_tool_exclusion unique (workcode, tool_capability_id)
);

create index idx_tool_exclusion_subject_mcp
    on subject_tool_exclusion (workcode, mcp_capability_id, enabled);

create table menu_permission_grant (
    id uuid primary key,
    subject_type varchar(16) not null,
    subject_ref varchar(120) not null,
    menu_key varchar(80) not null,
    enabled boolean not null,
    created_by varchar(40) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    revoked_at timestamp with time zone,
    constraint uq_menu_permission_grant unique (subject_type, subject_ref, menu_key)
);

create index idx_menu_permission_subject
    on menu_permission_grant (subject_type, subject_ref, enabled);
create index idx_menu_permission_menu
    on menu_permission_grant (menu_key, enabled);

update subject_grant
set enabled = false,
    revoked_at = current_timestamp
where enabled = true
  and capability_id in (select id from capability where type = 'INSTRUCTION');
