#!/bin/sh
set -eu
: "${MYSQL_PASSWORD:?Missing database password}"
: "${MYSQL_USER:?Missing database user}"
: "${MYSQL_DATABASE:?Missing database name}"
export MYSQL_PWD="$MYSQL_PASSWORD"
exec mysqldump -u"$MYSQL_USER" --single-transaction --no-tablespaces --set-gtid-purged=OFF --result-file="$1" "$MYSQL_DATABASE"
