#!/usr/bin/env bash
#
# Lightweight PostgreSQL metrics sampler for Assignment 3.
# Outputs CSV with TPS, active connections, lock waits.
#

set -euo pipefail

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-chat}"
PGDATABASE="${PGDATABASE:-chatdb}"
INTERVAL="${INTERVAL:-2}"
OUT="${OUT:-db_metrics_$(date -u +%Y%m%dT%H%M%SZ).csv}"

if ! command -v psql >/dev/null 2>&1; then
  echo "psql is required"
  exit 1
fi

echo "ts,xact_commit,xact_rollback,active_connections,lock_waiting" > "$OUT"

while true; do
  TS="$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)"
  ROW="$(psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" -Atc "
    SELECT
      (SELECT sum(xact_commit) FROM pg_stat_database),
      (SELECT sum(xact_rollback) FROM pg_stat_database),
      (SELECT count(*) FROM pg_stat_activity WHERE state = 'active'),
      (SELECT count(*) FROM pg_locks l JOIN pg_stat_activity a ON l.pid = a.pid WHERE NOT l.granted);
  " 2>/dev/null || echo '0|0|0|0')"
  echo "$TS,$(echo "$ROW" | tr '|' ',')" >> "$OUT"
  sleep "$INTERVAL"
done
