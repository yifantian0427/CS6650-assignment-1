# consumer

Message queue **consumer** that distributes chat messages to WebSocket clients (Assignment 2).

- Pulls messages from RabbitMQ (one queue per room or topic subscription).
- Routes by `roomId` to a room manager.
- Broadcasts to all connected WebSocket sessions in that room.
- Multi-threaded consumer pool; configurable thread count (10, 20, 40, 80).
- At-least-once delivery; duplicate handling and retry logic.
- Health check endpoint for deployment monitoring.

Run on a **separate EC2 instance** from the WebSocket servers.  
Build: `mvn -pl consumer package`  
Run: `java -jar target/consumer-1.0-SNAPSHOT.jar`
