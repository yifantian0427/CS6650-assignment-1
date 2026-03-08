#!/usr/bin/env bash
#
# Install consumer as a systemd service on an EC2 instance.
#
# Usage on EC2:
#   sudo ./install-systemd-consumer.sh /full/path/to/consumer.jar
#

set -euo pipefail

JAR_SRC="${1:-}"
if [ -z "$JAR_SRC" ] || [ ! -f "$JAR_SRC" ]; then
  echo "Usage: sudo $0 /path/to/consumer.jar"
  exit 1
fi

sudo mkdir -p /opt/cs6650
sudo mkdir -p /etc/cs6650

sudo cp "$JAR_SRC" /opt/cs6650/consumer.jar
sudo chown ec2-user:ec2-user /opt/cs6650/consumer.jar || true

if [ ! -f /etc/cs6650/consumer.env ]; then
  sudo cp "$(dirname "$0")/../systemd/consumer.env.example" /etc/cs6650/consumer.env
  echo "Created /etc/cs6650/consumer.env (edit RabbitMQ host + app.servers.urls)"
fi

sudo cp "$(dirname "$0")/../systemd/consumer.service" /etc/systemd/system/consumer.service

sudo systemctl daemon-reload
sudo systemctl enable consumer
sudo systemctl restart consumer

echo "Installed and started: consumer"
echo "Check status: sudo systemctl status consumer"
echo "Logs: sudo journalctl -u consumer -f"

