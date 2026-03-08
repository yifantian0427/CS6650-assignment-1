#!/usr/bin/env bash
#
# Poll RabbitMQ Management API and output queue metrics as CSV.
#
# Example:
#   ./queue-depth.sh --host 10.0.1.50 --user admin --pass secret --rooms 20 --interval 2 --out queue_depth.csv
#
# Notes:
# - Requires RabbitMQ management plugin enabled (port 15672).
# - Uses jq if available; falls back to python3 json parsing.
#

set -euo pipefail

HOST="localhost"
PORT="15672"
USER="guest"
PASS="guest"
VHOST="/"
ROOMS="20"
INTERVAL="2"
OUT=""
ONCE="0"

usage() {
  echo "Usage: $0 [--host HOST] [--port 15672] [--user USER] [--pass PASS] [--vhost VHOST] [--rooms N] [--interval SECONDS] [--out FILE] [--once]"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --host) HOST="$2"; shift 2 ;;
    --port) PORT="$2"; shift 2 ;;
    --user) USER="$2"; shift 2 ;;
    --pass) PASS="$2"; shift 2 ;;
    --vhost) VHOST="$2"; shift 2 ;;
    --rooms) ROOMS="$2"; shift 2 ;;
    --interval) INTERVAL="$2"; shift 2 ;;
    --out) OUT="$2"; shift 2 ;;
    --once) ONCE="1"; shift 1 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1"; usage; exit 1 ;;
  esac
done

if [ -z "$OUT" ]; then
  OUT="queue_depth_$(date -u +%Y%m%dT%H%M%SZ).csv"
fi

encode() {
  python3 - <<'PY' "$1"
import sys, urllib.parse
print(urllib.parse.quote(sys.argv[1], safe=""))
PY
}

VHOST_ENC="$(encode "$VHOST")"

write_header_if_needed() {
  if [ ! -f "$OUT" ]; then
    echo "ts,queue,messages,messages_ready,messages_unack,publish_rate,deliver_rate" > "$OUT"
  fi
}

fetch_queue_json() {
  local q="$1"
  curl -sS -u "$USER:$PASS" \
    "http://${HOST}:${PORT}/api/queues/${VHOST_ENC}/${q}"
}

extract_fields() {
  # Input: JSON on stdin. Output: messages, ready, unack, pubRate, deliverRate
  if command -v jq >/dev/null 2>&1; then
    jq -r '[
      (.messages // 0),
      (.messages_ready // 0),
      (.messages_unacknowledged // 0),
      (.message_stats.publish_details.rate // 0),
      (.message_stats.deliver_get_details.rate // 0)
    ] | @tsv'
  else
    python3 - <<'PY'
import sys, json
obj=json.load(sys.stdin)
def g(path, default=0.0):
    cur=obj
    for p in path:
        if not isinstance(cur, dict) or p not in cur:
            return default
        cur=cur[p]
    return cur
vals = [
  int(g(["messages"], 0)),
  int(g(["messages_ready"], 0)),
  int(g(["messages_unacknowledged"], 0)),
  float(g(["message_stats","publish_details","rate"], 0.0)),
  float(g(["message_stats","deliver_get_details","rate"], 0.0)),
]
print("\t".join(str(v) for v in vals))
PY
  fi
}

write_header_if_needed

while true; do
  TS="$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)"
  for i in $(seq 1 "$ROOMS"); do
    Q="room.${i}"
    JSON="$(fetch_queue_json "$Q" || true)"
    if [ -z "$JSON" ]; then
      echo "${TS},${Q},0,0,0,0,0" >> "$OUT"
      continue
    fi
    FIELDS="$(printf "%s" "$JSON" | extract_fields || true)"
    if [ -z "$FIELDS" ]; then
      echo "${TS},${Q},0,0,0,0,0" >> "$OUT"
      continue
    fi
    MESSAGES="$(echo "$FIELDS" | awk '{print $1}')"
    READY="$(echo "$FIELDS" | awk '{print $2}')"
    UNACK="$(echo "$FIELDS" | awk '{print $3}')"
    PUBRATE="$(echo "$FIELDS" | awk '{print $4}')"
    DELRATE="$(echo "$FIELDS" | awk '{print $5}')"
    echo "${TS},${Q},${MESSAGES},${READY},${UNACK},${PUBRATE},${DELRATE}" >> "$OUT"
  done

  if [ "$ONCE" = "1" ]; then
    break
  fi
  sleep "$INTERVAL"
done

echo "Wrote: $OUT"

