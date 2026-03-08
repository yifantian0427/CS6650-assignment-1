#!/usr/bin/env bash
#
# Run a client command while collecting RabbitMQ queue metrics to CSV.
#
# Example:
#   ./collect-metrics.sh \
#     --rabbit-host 10.0.1.50 --rabbit-user admin --rabbit-pass secret \
#     --rooms 20 --interval 2 \
#     --client-cmd "java -jar /full/path/client-part1.jar" \
#     --out-dir /tmp/run1
#

set -euo pipefail

RABBIT_HOST="localhost"
RABBIT_PORT="15672"
RABBIT_USER="guest"
RABBIT_PASS="guest"
RABBIT_VHOST="/"
ROOMS="20"
INTERVAL="2"
OUT_DIR=""
CLIENT_CMD=""

usage() {
  echo "Usage: $0 --client-cmd \"<command>\" [--rabbit-host HOST] [--rabbit-port 15672] [--rabbit-user USER] [--rabbit-pass PASS] [--rabbit-vhost VHOST] [--rooms N] [--interval SECONDS] [--out-dir DIR]"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --rabbit-host) RABBIT_HOST="$2"; shift 2 ;;
    --rabbit-port) RABBIT_PORT="$2"; shift 2 ;;
    --rabbit-user) RABBIT_USER="$2"; shift 2 ;;
    --rabbit-pass) RABBIT_PASS="$2"; shift 2 ;;
    --rabbit-vhost) RABBIT_VHOST="$2"; shift 2 ;;
    --rooms) ROOMS="$2"; shift 2 ;;
    --interval) INTERVAL="$2"; shift 2 ;;
    --out-dir) OUT_DIR="$2"; shift 2 ;;
    --client-cmd) CLIENT_CMD="$2"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown arg: $1"; usage; exit 1 ;;
  esac
done

if [ -z "$CLIENT_CMD" ]; then
  echo "Missing --client-cmd"
  usage
  exit 1
fi

if [ -z "$OUT_DIR" ]; then
  OUT_DIR="run_$(date -u +%Y%m%dT%H%M%SZ)"
fi

mkdir -p "$OUT_DIR"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
QUEUE_DEPTH_OUT="${OUT_DIR}/queue_depth.csv"
CONSUMER_LAG_OUT="${OUT_DIR}/consumer_lag.csv"
CLIENT_OUT="${OUT_DIR}/client_stdout.txt"
CLIENT_ERR="${OUT_DIR}/client_stderr.txt"

echo "Output directory: $OUT_DIR"
echo "Client command: $CLIENT_CMD"

set +e

# Start queue depth polling
"$SCRIPT_DIR/queue-depth.sh" \
  --host "$RABBIT_HOST" --port "$RABBIT_PORT" \
  --user "$RABBIT_USER" --pass "$RABBIT_PASS" \
  --vhost "$RABBIT_VHOST" --rooms "$ROOMS" \
  --interval "$INTERVAL" --out "$QUEUE_DEPTH_OUT" &
QDEPTH_PID=$!

# Start lag polling
"$SCRIPT_DIR/consumer-lag.sh" \
  --host "$RABBIT_HOST" --port "$RABBIT_PORT" \
  --user "$RABBIT_USER" --pass "$RABBIT_PASS" \
  --vhost "$RABBIT_VHOST" --rooms "$ROOMS" \
  --interval "$INTERVAL" --out "$CONSUMER_LAG_OUT" &
LAG_PID=$!

# Run the client command
bash -lc "$CLIENT_CMD" >"$CLIENT_OUT" 2>"$CLIENT_ERR"
CLIENT_EXIT=$?

# Stop polling
kill "$QDEPTH_PID" >/dev/null 2>&1 || true
kill "$LAG_PID" >/dev/null 2>&1 || true

wait "$QDEPTH_PID" >/dev/null 2>&1 || true
wait "$LAG_PID" >/dev/null 2>&1 || true

set -e

echo "Client exit code: $CLIENT_EXIT"
echo "Wrote:"
echo "  $QUEUE_DEPTH_OUT"
echo "  $CONSUMER_LAG_OUT"
echo "  $CLIENT_OUT"
echo "  $CLIENT_ERR"

exit "$CLIENT_EXIT"

