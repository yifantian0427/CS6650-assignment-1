# server-v2

WebSocket chat server with **message queue integration** (Assignment 2).

- Accepts WebSocket connections at `/chat/{roomId}`.
- Validates messages (userId, username, message, timestamp, messageType).
- **Publishes** valid messages to RabbitMQ (topic exchange `chat.exchange`, routing key `room.{roomId}`) in the required JSON format.
- Uses a channel pool and circuit breaker for queue failures.
- Exposes `/health` for ALB health checks.

**Queue message format:** `messageId`, `roomId`, `userId`, `username`, `message`, `timestamp`, `messageType`, `serverId`, `clientIp`.

Build: `mvn -pl server-v2 package`  
Run: `java -jar target/server-v2-1.0-SNAPSHOT.jar`
