# consumer-v3

Assignment 3 consumer with persistence and write-behind design.

## Pipeline

`RabbitMQ queue -> Consumer threads -> Broadcast to server-v2 -> Persistence queue -> DB writer pool`

## Key features

- Configurable batch writes (`app.persistence.batch-size`)
- Flush interval tuning (`app.persistence.flush-interval-ms`)
- Separate DB writer thread pool (`app.persistence.db-writer-threads`)
- Idempotent writes using `message_id` primary key + `ON CONFLICT DO NOTHING`
- Retry with exponential backoff (`app.retry.*`)
- Dead letter storage in `failed_messages`
- DB circuit breaker (`app.db.*`)

## Run

```bash
mvn -pl consumer-v3 package -DskipTests
java -jar consumer-v3/target/consumer-v3-1.0-SNAPSHOT.jar
```

## Health endpoint

`GET /health` returns:

- messages consumed
- broadcast errors
- enqueue errors
- DB persisted count
- dead letter count
- DB queue size
