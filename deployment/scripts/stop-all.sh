#!/usr/bin/env bash
# Stop server-v2 and consumer on remote hosts (kills java processes running the JARs).
# Usage: export DEPLOY_KEY SERVER_HOSTS CONSUMER_HOST; ./stop-all.sh

REMOTE_USER="${REMOTE_USER:-ec2-user}"

if [ -z "${DEPLOY_KEY:-}" ]; then
  echo "Set DEPLOY_KEY (path to .pem)"
  exit 1
fi

SSH_OPTS="-i $DEPLOY_KEY -o StrictHostKeyChecking=no"

for HOST in ${SERVER_HOSTS:-}; do
  echo "Stopping server-v2 on $HOST ..."
  ssh $SSH_OPTS "$REMOTE_USER@$HOST" "pkill -f 'java -jar server-v2.jar' || true"
done

if [ -n "${CONSUMER_HOST:-}" ]; then
  echo "Stopping consumer on $CONSUMER_HOST ..."
  ssh $SSH_OPTS "$REMOTE_USER@$CONSUMER_HOST" "pkill -f 'java -jar consumer.jar' || true"
fi

echo "Done."
