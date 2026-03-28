# monitoring

Scripts and tools for queue and system metrics (Assignment 2).

## Contents

- **scripts/**
  - `queue-depth.sh` – Poll RabbitMQ management API for queue depths over time
  - `consumer-lag.sh` – Consumer lag metrics
  - `collect-metrics.sh` – Run load test and collect client + queue + system metrics
  - `db-metrics.sh` – Sample PostgreSQL transaction, connection, lock-wait metrics

## Quick usage

### Queue depth (CSV)

```bash
cd monitoring/scripts
chmod +x *.sh
./queue-depth.sh --host <RABBIT_HOST> --user <USER> --pass <PASS> --rooms 20 --interval 2 --out queue_depth.csv
```

### Consumer lag estimate (CSV)

```bash
./consumer-lag.sh --host <RABBIT_HOST> --user <USER> --pass <PASS> --rooms 20 --interval 2 --out consumer_lag.csv
```

### Collect queue metrics while running a client

```bash
./collect-metrics.sh \
  --rabbit-host <RABBIT_HOST> --rabbit-user <USER> --rabbit-pass <PASS> \
  --rooms 20 --interval 2 \
  --client-cmd "<your client command>" \
  --out-dir run1
```

### Database metrics (CSV)

```bash
PGHOST=<DB_HOST> PGPORT=5432 PGUSER=chat PGDATABASE=chatdb \
  ./db-metrics.sh
```

## Metrics to Collect

- **Client:** Total runtime, messages/sec, connection failures, retries
- **Queue:** Peak and average queue depth, consumer rate, producer rate
- **System:** CPU, memory, network I/O (e.g. from EC2 or CloudWatch)

RabbitMQ Management UI (port 15672) for queue depth trends and message rates.
