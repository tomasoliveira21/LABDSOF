#!/bin/sh
# One database and one login role per owner: the five services and Keycloak (ADR-003, ADR-011, #189).
# Run once by the image's entrypoint, on the first start with an empty volume (docker-entrypoint-initdb.d).
# To apply a change here, reset the volume: docker compose -f infra/compose.yaml down -v
set -eu

# Nobody but the owner (and the superuser) may connect to a database: new databases grant CONNECT and
# TEMPORARY to PUBLIC by default, so that grant is revoked on every database, the default ones included.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<'SQL'
REVOKE ALL ON DATABASE postgres FROM PUBLIC;
REVOKE ALL ON DATABASE template1 FROM PUBLIC;
SQL

create_owned_database() {
  name="$1"
  password="$2"
  if [ -z "$password" ]; then
    echo "10-create-databases.sh: the password of '$name' is empty; set it in infra/.env" >&2
    exit 1
  fi
  # psql variables (:"name" quotes an identifier, :'password' a literal), so no value is pasted into SQL.
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
       --set=name="$name" --set=password="$password" <<'SQL'
CREATE ROLE :"name" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD :'password';
CREATE DATABASE :"name" OWNER :"name";
REVOKE ALL ON DATABASE :"name" FROM PUBLIC;
SQL
}

create_owned_database auth_users "${AUTH_USERS_DB_PASSWORD:-}"
create_owned_database events     "${EVENTS_DB_PASSWORD:-}"
create_owned_database reports    "${REPORTS_DB_PASSWORD:-}"
create_owned_database transports "${TRANSPORTS_DB_PASSWORD:-}"
create_owned_database badges     "${BADGES_DB_PASSWORD:-}"
create_owned_database keycloak   "${KEYCLOAK_DB_PASSWORD:-}"
