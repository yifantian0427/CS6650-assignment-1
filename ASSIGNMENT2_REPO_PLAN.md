# CS6650 Assignment 2 – Repository Plan

## Directory Layout

```
cs6650 assignment1/
├── pom.xml                    # Root POM (add server-v2, consumer modules)
├── server/                    # [Assignment 1] Original WebSocket server (unchanged for reference)
├── client-part1/              # [Assignment 1] Load test client Part 1
├── client-part2/              # [Assignment 1] Load test client Part 2
│
├── server-v2/                 # [NEW] WebSocket server with queue integration
│   ├── pom.xml
│   └── src/main/
│       ├── java/.../server/
│       │   ├── ChatServerApplication.java
│       │   ├── ChatWebSocketHandler.java   # Publish to queue instead of broadcast
│       │   ├── WebSocketConfig.java
│       │   ├── HealthController.java
│       │   ├── ChatMessage.java, ChatValidator.java, RoomIdInterceptor.java
│       │   ├── queue/
│       │   │   ├── ChannelPool.java         # RabbitMQ channel pool
│       │   │   ├── QueuePublisher.java      # Publish chat messages
│       │   │   ├── QueueConfig.java         # Exchange, queues, connection
│       │   │   └── CircuitBreaker.java      # Queue failure handling
│       │   └── dto/
│       │       └── QueueMessage.java         # messageId, roomId, userId, ...
│       └── resources/
│           └── application.properties       # server.port, rabbitmq.host, pool size
│
├── consumer/                  # [NEW] Queue consumer → WebSocket broadcaster
│   ├── pom.xml
│   └── src/main/
│       ├── java/.../consumer/
│       │   ├── ConsumerApplication.java
│       │   ├── QueueConsumer.java          # Pull from queue, route by room
│       │   ├── RoomManager.java            # roomId -> Set<WebSocketSession>
│       │   ├── WebSocketBroadcaster.java   # Send to all sessions in room
│       │   ├── ConsumerPool.java           # Multi-threaded consumer threads
│       │   └── config/
│       │       ├── ConsumerConfig.java     # thread count, prefetch, etc.
│       │       └── HealthController.java   # /health for deployment
│       └── resources/
│           └── application.properties      # RabbitMQ, thread count, room list
│
├── deployment/                # [NEW] ALB and deployment scripts
│   ├── README.md              # How to deploy
│   ├── alb/                   # ALB configuration (AWS CLI or Terraform)
│   │   ├── create-alb.sh      # Target groups, listener, sticky session
│   │   └── alb-params.json    # Optional JSON params
│   ├── scripts/
│   │   ├── deploy-server.sh   # SCP + SSH run server-v2 on EC2
│   │   ├── deploy-consumer.sh # Deploy consumer on EC2
│   │   └── start-all.sh       # Start server(s) and consumer
│   └── docker/                # Optional: Dockerfiles for server-v2 and consumer
│
└── monitoring/               # [NEW] Metrics and monitoring
    ├── README.md
    ├── scripts/
    │   ├── queue-depth.sh     # Poll RabbitMQ API for queue depths
    │   ├── consumer-lag.sh    # Consumer lag metrics
    │   └── collect-metrics.sh # Run tests and collect client/queue/system metrics
    ├── config/
    │   └── cloudwatch-alarms.json  # Optional: ALB/SQS alarms
    └── tools/                 # Optional: Micrometer/Prometheus config
```

## Module Summary

| Path | Purpose | Tech |
|------|--------|------|
| **server-v2** | WebSocket server that publishes valid messages to RabbitMQ (topic `chat.exchange`, routing `room.{roomId}`). Connection pool, circuit breaker. | Java 17, Spring Boot, RabbitMQ client |
| **consumer** | Standalone app: consume from queue → route by roomId → broadcast to in-memory WebSocket sessions. Multi-threaded consumer pool, at-least-once, health check. | Java 17, Spring Boot (or plain Java), RabbitMQ client |
| **deployment** | Scripts and config to create ALB, target groups, sticky sessions, and to deploy server-v2 + consumer on EC2. | Shell, AWS CLI (or Terraform) |
| **monitoring** | Scripts to capture queue depth, consumer lag, client throughput; optional CloudWatch/application metrics. | Shell, optional Micrometer/Prometheus |

## Build and Run Order

1. **Queue (RabbitMQ)** – Run on its own EC2 (or use managed RabbitMQ).
2. **server-v2** – Build `mvn -pl server-v2 package`, run on one or more EC2 instances (behind ALB).
3. **consumer** – Build `mvn -pl consumer package`, run on separate EC2; connects to RabbitMQ and maintains WebSocket sessions (consumers need to connect to the same WebSocket endpoint as clients, or you need a shared session store; see architecture doc).
4. **ALB** – Create via `deployment/alb/` scripts; point clients at ALB URL; health check `/health`.

## Notes for Architecture Doc

- **Message flow:** Client → ALB → server-v2 → publish to RabbitMQ → consumer pulls → RoomManager → broadcast to WebSocket sessions in that room.
- **Session affinity:** ALB sticky session so a client stays on one server-v2 instance; consumer must have a way to reach “all sessions in room” (e.g., consumer holds all WebSocket connections, or server-v2 holds connections and consumer sends “broadcast” commands back to servers—clarify in doc).
- **Queue topology:** One queue per room (room.1–room.20) or single queue with routing; document in “Queue topology design”.
- **Failure handling:** Circuit breaker in server-v2, consumer retry and DLQ, ALB unhealthy target removal.

## Submission Checklist

- [ ] Git repo with: `server-v2/`, `consumer/`, `deployment/`, `monitoring/`
- [ ] Architecture doc (2 pages): system diagram, message flow, queue topology, consumer model, ALB, failure handling
- [ ] Test results: single instance + 2 and 4 instances (client output, queue metrics, ALB distribution)
- [ ] Config details: queue, consumer, ALB, instance types
