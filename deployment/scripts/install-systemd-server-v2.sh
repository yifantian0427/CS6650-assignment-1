#!/usr/bin/env bash
#
# Install server-v2 as a systemd service on an EC2 instance.
#
# Usage on EC2:
#   sudo ./install-systemd-server-v2.sh /full/path/to/server-v2.jar
#

set -euo pipefail

JAR_SRC="${1:-}"
if [ -z "$JAR_SRC" ] || [ ! -f "$JAR_SRC" ]; then
  echo "Usage: sudo $0 /path/to/server-v2.jar"
  exit 1
fi

sudo mkdir -p /opt/cs6650
sudo mkdir -p /etc/cs6650

sudo cp "$JAR_SRC" /opt/cs6650/server-v2.jar
sudo chown ec2-user:ec2-user /opt/cs6650/server-v2.jar || true

if [ ! -f /etc/cs6650/server-v2.env ]; then
  sudo cp "$(dirname "$0")/../systemd/server-v2.env.example" /etc/cs6650/server-v2.env
  echo "Created /etc/cs6650/server-v2.env (edit it with RabbitMQ host, server id, tuning)"
fi

sudo cp "$(dirname "$0")/../systemd/server-v2.service" /etc/systemd/system/server-v2.service

sudo systemctl daemon-reload
sudo systemctl enable server-v2
sudo systemctl restart server-v2

echo "Installed and started: server-v2"
echo "Check status: sudo systemctl status server-v2"
echo "Logs: sudo journalctl -u server-v2 -f"

