#!/bin/sh
# Invoked only by the explicit local reset-demo action, after a successful backup.
set -eu
test "${UTBRIDORA_DEV_RESET:-}" = REPLACE_WITH_PORTABLE_SYNTHETIC || { echo 'Missing explicit developer reset scope' >&2; exit 1; }
test "${MYSQL_DATABASE:-}" = utbridora || { echo 'Unexpected database name' >&2; exit 1; }
: "${MYSQL_ROOT_PASSWORD:?Missing local MySQL root password}"
export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
mysql --host=127.0.0.1 --user=root <<'SQL'
DROP DATABASE `utbridora`;
CREATE DATABASE `utbridora` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
SQL
