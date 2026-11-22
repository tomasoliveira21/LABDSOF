-- US-28 (#75, spec D5): the member's name, copied from the token's name claim when the change is recorded, so the
-- history says who acted under the name they had. Null when the token has none. UserDeleted removes it (API outline
-- §7.4, US-05) and keeps actor_user_id.
ALTER TABLE help_request_change ADD COLUMN actor_name text;
