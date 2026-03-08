#!/usr/bin/env bash
#
# Estimate consumer lag per room queue using RabbitMQ Management API.
#
# Lag estimate (ms) ≈ (messages_ready / deliver_rate) * 1000
# - If deliver_rate is 0, lag_ms is "inf".
#

set -euo pipefail

HOST="localhost"
PORT="15672"
USER="guest"
PASS="guest"
VHOST="/"
ROOMS="20"
OUT=""
ONCE="0"
INTERVAL="2"

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
  OUT="consumer_lag_$(date -u +%Y%m%dT%H%M%SZ).csv"
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
    echo "ts,queue,messages_ready,deliver_rate,lag_ms" > "$OUT"
  fi
}

extract_ready_and_rate() {
  if command -v jq >/dev/null 2>&1; then
    jq -r '[
      (.messages_ready // 0),
      (.message_stats.deliver_get_details.rate // 0)
    ] | @tsv'
  else
    python3 - <<'PY'
import sys, json
obj=json.load(sys.stdin)
ready=int(obj.get("messages_ready",0) or 0)
rate=float(((obj.get("message_stats") or {}).get("deliver_get_details") or {}).get("rate") or 0.0)
print(f"{ready}\t{rate}")
PY
  fi
}

write_header_if_needed

while true; do
  TS="$(date -u +%Y-%m-%dT%H:%M:%S.%3NZ)"
  for i in $(seq 1 "$ROOMS"); do
    Q="room.${i}"
    JSON="$(curl -sS -u "$USER:$PASS" "http://${HOST}:${PORT}/api/queues/${VHOST_ENC}/${Q}" || true)"
    if [ -z "$JSON" ]; then
      echo "${TS},${Q},0,0,inf" >> "$OUT"
      continue
    fi
    FIELDS="$(printf "%s" "$JSON" | extract_ready_and_rate || true)"
    READY="$(echo "$FIELDS" | awk '{print $1}')"
    RATE="$(echo "$FIELDS" | awk '{print $2}')"
    LAG="$(python3 - <<'PY' "$READY" "$RATE"
import sys, math
ready=float(sys.argv[1])
rate=float(sys.argv[2])
if rate <= 0:
    print("inf")
else:
    print(int((ready / rate) * 1000))
PY
)"
    echo "${TS},${Q},${READY},${RATE},${LAG}" >> "$OUT"
  done
  if [ "$ONCE" = "1" ]; then
    break
  fi
  sleep "$INTERVAL"
done

echo "Wrote: $OUT"

