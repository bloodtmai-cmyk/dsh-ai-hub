-- Existing bindings predate Hub-managed model endpoint metadata. Keep them
-- unset so clients fail closed and an administrator must reissue the grant;
-- silently assigning a local or deployment-specific endpoint is unsafe.
alter table api_key_binding
    add column provider varchar(80);

alter table api_key_binding
    add column base_url varchar(1000);
