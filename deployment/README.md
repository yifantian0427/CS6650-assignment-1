# deployment

ALB configuration and deployment scripts for Assignment 2.

## Contents

- **alb/** – Application Load Balancer
  - `create-alb.sh` – Create ALB, target group (health check, sticky sessions), listener
  - `params.env.example` – Copy to `params.env` and set VPC, subnets, security groups, instance IDs
  - `register-targets.sh` – Register server-v2 instances with the target group

- **scripts/** – Deploy and run on EC2
  - `deploy-server.sh` – SCP server-v2 JAR to one or more hosts
  - `deploy-consumer.sh` – SCP consumer JAR to consumer host
  - `start-all.sh` – SSH and start server-v2 + consumer in background
  - `stop-all.sh` – Stop server-v2 and consumer processes on remote hosts
  - `install-systemd-server-v2.sh` – Install server-v2 as a systemd service (auto-restart)
  - `install-systemd-consumer.sh` – Install consumer as a systemd service (auto-restart)

- **systemd/** – Service unit templates and env examples
  - `server-v2.service`, `server-v2.env.example`
  - `consumer.service`, `consumer.env.example`

## Architecture

```
Client → ALB (port 80) → [Server1, Server2, ...] (port 8080) → RabbitMQ → Consumer
                                                                    ↓
                                            POST /internal/broadcast ← Consumer
```

- **ALB:** Health check `/health`, interval 30s, timeout 5s, healthy 2, unhealthy 3. Sticky sessions enabled. Idle timeout **> 60s** for WebSocket.
- **Server-v2:** Run on one or more EC2 instances; register their instance IDs in the target group.
- **Consumer:** Run on a separate EC2 instance; set `app.servers.urls` to the server-v2 base URLs (private IPs or ALB) so it can POST broadcast requests.

---

## 1. ALB setup

### Prerequisites

- AWS CLI installed and configured (`aws configure`)
- VPC with at least 2 subnets (different AZs)
- Security group for ALB: inbound 80 (and 22 if you SSH from same SG)
- Security group for server-v2 instances: inbound 8080 from ALB SG, 22 for SSH
- EC2 instances for server-v2 (Amazon Linux 2 / 2023 or Ubuntu, Java 17)

### Steps

1. Copy and edit parameters:
   ```bash
   cd deployment/alb
   cp params.env.example params.env
   # Edit params.env: VPC_ID, SUBNET_IDS, ALB_SECURITY_GROUP_ID, SERVER_SECURITY_GROUP_ID, SERVER_INSTANCE_IDS
   ```

2. Create ALB and target group:
   ```bash
   chmod +x create-alb.sh register-targets.sh
   source params.env
   ./create-alb.sh
   ```

3. If you add more server instances later:
   ```bash
   ./register-targets.sh i-xxxxxxxx i-yyyyyyyy
   ```

4. Point clients at the ALB DNS (printed at the end of `create-alb.sh`):
   - WebSocket: `ws://<ALB_DNS>/chat/<roomId>`
   - Health: `http://<ALB_DNS>/health`

---

## 2. Deploy server-v2 and consumer

### Build JARs (from repo root)

```bash
mvn -pl server-v2 package -DskipTests
mvn -pl consumer package -DskipTests
```

### Deploy server-v2 to multiple hosts

```bash
cd deployment/scripts
chmod +x deploy-server.sh deploy-consumer.sh start-all.sh stop-all.sh

export DEPLOY_KEY="/full/path/to/your-key.pem"
export SERVER_HOSTS="10.0.1.10 10.0.1.11"   # private IPs of server-v2 instances

./deploy-server.sh
# Or: ./deploy-server.sh /path/to/key.pem 10.0.1.10 10.0.1.11
```

### Deploy consumer to one host

```bash
export CONSUMER_HOST="10.0.1.20"   # private IP of consumer instance
./deploy-consumer.sh
# Or: ./deploy-consumer.sh /path/to/key.pem 10.0.1.20
```

### Start all (server-v2 + consumer)

Set `CONSUMER_SERVER_URLS` so the consumer can POST to each server-v2 instance:

```bash
export DEPLOY_KEY="/path/to/key.pem"
export SERVER_HOSTS="10.0.1.10 10.0.1.11"
export CONSUMER_HOST="10.0.1.20"
export CONSUMER_SERVER_URLS="http://10.0.1.10:8080,http://10.0.1.11:8080"

./start-all.sh
```

Ensure RabbitMQ is running and reachable from both server-v2 and consumer (same VPC or security group). On each server-v2 host, set `app.queue.host` to the RabbitMQ host if not localhost.

### Stop all

```bash
export DEPLOY_KEY="/path/to/key.pem"
export SERVER_HOSTS="10.0.1.10 10.0.1.11"
export CONSUMER_HOST="10.0.1.20"
./stop-all.sh
```

---

## 3. Configuration summary

| Component   | Port | Key config |
|------------|------|------------|
| ALB        | 80   | Target group → server-v2 instances:8080 |
| Server-v2  | 8080 | `app.queue.host`, `app.server.id` (e.g. server-1, server-2) |
| Consumer   | 8081 | `app.rabbitmq.host`, `app.servers.urls` (server-v2 base URLs) |
| RabbitMQ   | 5672 | On separate EC2 or Docker; allow 5672 from server-v2 and consumer SGs |
