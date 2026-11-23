#!/bin/sh
set -eu
# Refresh once BEFORE clamd's stale-database startup guard. This matters when a
# pinned image is deployed weeks later. Failure leaves scanner/worker unavailable.
# FreshClam enforces its own bounded attempts and CDN cooldown; do not retry-loop it.
mkdir -p /var/lib/clamav
chown -R clamav:clamav /var/lib/clamav
freshclam --foreground --stdout --config-file=/etc/clamav/freshclam.conf
# The official init starts the normal long-lived updater and clamd processes.
exec /init
