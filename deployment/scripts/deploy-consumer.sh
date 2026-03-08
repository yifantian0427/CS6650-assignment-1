#!/usr/bin/env bash
#
# Deploy consumer JAR to an EC2 host (typically one instance).
# Build first: mvn -pl consumer package -DskipTests
#
# Required env (or pass as arguments):
#   DEPLOY_KEY    - Path to SSH private key (.pem)
#   CONSUMER_HOST - Hostname or IP of the consumer instance
# Optional:
#   REMOTE_USER  - SSH user (default: ec2-user)
#   REMOTE_DIR   - Directory on remote host (default: /home/ec2-user)
#   SERVER_URLS   - Comma-separated server-v2 URLs for app.servers.urls (set on remote via env or config)
#

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
JAR_PATH="$PROJECT_ROOT/consumer/target/consumer-1.0-SNAPSHOT.jar"

REMOTE_USER="${REMOTE_USER:-ec2-user}"
REMOTE_DIR="${REMOTE_DIR:-/home/$REMOTE_USER}"

if [ -n "${1:-}" ]; then
  DEPLOY_KEY="$1"
fi
if [ -n "${2:-}" ]; then
  CONSUMER_HOST="$2"
fi

if [ -z "${DEPLOY_KEY:-}" ] || [ -z "${CONSUMER_HOST:-}" ]; then
  echo "Usage: $0 <path-to-.pem> <consumer-host>"
  echo "   Or: export DEPLOY_KEY and CONSUMER_HOST, then run $0"
  exit 1
fi

if [ ! -f "$JAR_PATH" ]; then
  echo "Build consumer first: mvn -pl consumer package -DskipTests"
  exit 1
fi

echo "Deploying consumer to $CONSUMER_HOST ..."
scp -i "$DEPLOY_KEY" -o StrictHostKeyChecking=accept-new "$JAR_PATH" \
  "$REMOTE_USER@$CONSUMER_HOST:${REMOTE_DIR}/consumer.jar"
echo "  -> $REMOTE_USER@$CONSUMER_HOST:${REMOTE_DIR}/consumer.jar"
echo "Done. On the host, set app.servers.urls (e.g. http://server1:8080,http://server2:8080) and run:"
echo "  java -jar $REMOTE_DIR/consumer.jar"
echo "Or with env: APP_SERVERS_URLS=http://ip1:8080,http://ip2:8080 java -jar $REMOTE_DIR/consumer.jar"
