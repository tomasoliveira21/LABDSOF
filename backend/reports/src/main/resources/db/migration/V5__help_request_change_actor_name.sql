-- US-28 (#75, spec D5): the member's name, copied from the token's name claim when the change is recorded, so the
-- history says who acted under the name they had. Null when the token has none; at most 200 characters (Actor).
-- Personal data: API outline §7.4 plans to remove it on UserDeleted (US-05), keeping actor_user_id. Reports does not
-- consume UserDeleted yet, so that removal is not built.
ALTER TABLE help_request_change ADD COLUMN actor_name text;
