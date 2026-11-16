#!/bin/sh
# One-shot set-up of the broker (#212), run by the `rabbitmq-setup` Compose service right after the broker is
# healthy, on every `up`: safe to repeat.
#  1. Imports infra/rabbitmq/topology.json: exchanges, the queues of API outline §7.5, their dead-letter queues
#     and bindings. Existing queues and their messages are kept; a queue whose arguments changed fails the
#     import (PRECONDITION_FAILED): reset with `docker compose -f infra/compose.yaml down -v`.
#  2. Declares one user per service with the password of infra/.env (a changed password is applied) and the
#     least privilege: declare nothing, write only to its own exchange and the dead-letter exchange, read only
#     its own queues and their dead-letter queues, no management tag.
# The broker's admin (RABBITMQ_ADMIN_USER) is passed through RABBITMQADMIN_USERNAME/PASSWORD and is used only
# here and by the operator; no service gets it.
set -eu

export RABBITMQADMIN_TARGET_HOST="${RABBITMQADMIN_TARGET_HOST:-rabbitmq}"
export RABBITMQADMIN_NON_INTERACTIVE_MODE=true

# The broker's health check (ping) passes before the management API listens: wait for it, at most 60 s.
attempt=0
until rabbitmqadmin show overview >/dev/null 2>&1; do
  attempt=$((attempt + 1))
  if [ "$attempt" -ge 30 ]; then
    echo "rabbitmq-setup: the management API of '$RABBITMQADMIN_TARGET_HOST' did not answer in 60 s" >&2
    exit 1
  fi
  sleep 2
done

rabbitmqadmin definitions import --file /etc/farol/topology.json
echo "rabbitmq-setup: topology imported"

declare_service_user() {
  user="$1"
  password="$2"
  write="$3"
  if [ -z "$password" ]; then
    echo "rabbitmq-setup: the password of '$user' is empty; set it in infra/.env" >&2
    exit 1
  fi
  rabbitmqadmin users declare --name "$user" --password "$password" --tags "" >/dev/null
  rabbitmqadmin permissions declare --user "$user" --configure '^$' --write "$write" --read "^${user}\\..*" >/dev/null
  echo "rabbitmq-setup: user '$user' declared"
}

declare_service_user auth-users "${RABBITMQ_AUTH_USERS_PASSWORD:-}" '^(farol\.auth-users|farol\.dead-letters)$'
declare_service_user events     "${RABBITMQ_EVENTS_PASSWORD:-}"     '^(farol\.events|farol\.dead-letters)$'
declare_service_user reports    "${RABBITMQ_REPORTS_PASSWORD:-}"    '^(farol\.reports|farol\.dead-letters)$'
declare_service_user transports "${RABBITMQ_TRANSPORTS_PASSWORD:-}" '^farol\.dead-letters$'
declare_service_user badges     "${RABBITMQ_BADGES_PASSWORD:-}"     '^farol\.dead-letters$'
echo "rabbitmq-setup: done"
