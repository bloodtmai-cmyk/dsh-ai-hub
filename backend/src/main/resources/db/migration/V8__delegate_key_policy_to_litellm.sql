update api_key_binding
set status = case when claimed_at is null then 'AVAILABLE' else 'CLAIMED' end
where status = 'EXPIRED';

alter table api_key_application drop column requested_models;

alter table api_key_binding drop column models;
alter table api_key_binding drop column quota;
alter table api_key_binding drop column expires_at;
