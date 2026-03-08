#!/usr/bin/env bash
#
# Start server-v2 on multiple hosts and consumer on one host via SSH.
# Requires: DEPLOY_KEY, SERVER_HOSTS, CONSUMER_HOST (and optionally RABBITMQ_HOST).
# Optional: REMOTE_USER, REMOTE_DIR.
# For consumer you must set server URLs (see deploy-consumer.sh).
#
# This script starts processes in the background on remote hosts. For production
# use a process manager (systemd, supervisor) or run interactively.
#

set -e

REMOTE_USER="${REMOTE_USER:-ec2-user}"
REMOTE_DIR="${REMOTE_DIR:-/home/$REMOTE_USER}"
# Comma-separated server-v2 base URLs for the consumer (internal IPs or ALB)
CONSUMER_SERVER_URLS="${CONSUMER_SERVER_URLS:-}"

if [ -z "${DEPLOY_KEY:-}" ] || [ -z "${SERVER_HOSTS:-}" ] || [ -z "${CONSUMER_HOST:-}" ]; then
  echo "Usage: export DEPLOY_KEY SERVER_HOSTS CONSUMER_HOST [CONSUMER_SERVER_URLS]"
  echo "  SERVER_HOSTS='ip1 ip2'  CONSUMER_HOST=ip3"
  echo "  CONSUMER_SERVER_URLS='http://ip1:8080,http://ip2:8080'  # for consumer app.servers.urls"
  echo "Then run: $0"
  exit 1
fi

SSH_OPTS="-i $DEPLOY_KEY -o StrictHostKeyChecking=no"

# Start server-v2 on each host (assumes RabbitMQ is reachable at app.queue.host on each host)
for HOST in $SERVER_HOSTS; do
  echo "Starting server-v2 on $HOST ..."
  ssh $SSH_OPTS "$REMOTE_USER@$HOST" \
    "cd $REMOTE_DIR && nohup java -jar server-v2.jar > server-v2.log 2>&1 &"
done

# Start consumer (needs app.servers.urls = list of server-v2 URLs)
if [ -z "$CONSUMER_SERVER_URLS" ]; then
  echo "WARNING: CONSUMER_SERVER_URLS not set. Consumer may not reach server-v2 instances."
fi
echo "Starting consumer on $CONSUMER_HOST ..."
if [ -n "$CONSUMER_SERVER_URLS" ]; then
  ssh $SSH_OPTS "$REMOTE_USER@$CONSUMER_HOST" \
    "cd $REMOTE_DIR && nohup java -Dapp.servers.urls='$CONSUMER_SERVER_URLS' -jar consumer.jar > consumer.log 2>&1 &"
else
  ssh $SSH_OPTS "$REMOTE_USER@$CONSUMER_HOST" \
    "cd $REMOTE_DIR && nohup java -jar consumer.jar > consumer.log 2>&1 &"
fi

echo "Done. Check logs: server-v2.log and consumer.log on each host."
