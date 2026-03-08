#!/usr/bin/env bash
#
# Deploy server-v2 JAR to one or more EC2 hosts.
# Build the JAR first: mvn -pl server-v2 package -DskipTests
#
# Required env (or pass as arguments):
#   DEPLOY_KEY   - Path to SSH private key (.pem)
#   SERVER_HOSTS - Space-separated list of hostnames or IPs
# Optional:
#   REMOTE_USER - SSH user (default: ec2-user)
#   REMOTE_DIR  - Directory on remote host (default: /home/ec2-user)
#

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
JAR_PATH="$PROJECT_ROOT/server-v2/target/server-v2-1.0-SNAPSHOT.jar"

REMOTE_USER="${REMOTE_USER:-ec2-user}"
REMOTE_DIR="${REMOTE_DIR:-/home/$REMOTE_USER}"

if [ -n "${1:-}" ]; then
  DEPLOY_KEY="$1"
fi
if [ -n "${2:-}" ]; then
  shift
  SERVER_HOSTS="$*"
fi

if [ -z "${DEPLOY_KEY:-}" ] || [ -z "${SERVER_HOSTS:-}" ]; then
  echo "Usage: $0 <path-to-.pem> <host1> [host2] [host3] ..."
  echo "   Or: export DEPLOY_KEY and SERVER_HOSTS, then run $0"
  exit 1
fi

if [ ! -f "$JAR_PATH" ]; then
  echo "Build server-v2 first: mvn -pl server-v2 package -DskipTests"
  exit 1
fi

for HOST in $SERVER_HOSTS; do
  echo "Deploying server-v2 to $HOST ..."
  scp -i "$DEPLOY_KEY" -o StrictHostKeyChecking=accept-new "$JAR_PATH" \
    "$REMOTE_USER@$HOST:${REMOTE_DIR}/server-v2.jar"
  echo "  -> $REMOTE_USER@$HOST:${REMOTE_DIR}/server-v2.jar"
done

echo "Done. Start with: start-all.sh or ssh and run: java -jar $REMOTE_DIR/server-v2.jar"
