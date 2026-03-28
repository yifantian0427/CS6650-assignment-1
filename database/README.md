# database

PostgreSQL schema for Assignment 3.

## Setup

1. Create database and user:

```sql
CREATE DATABASE chatdb;
CREATE USER chat WITH PASSWORD 'chatpass';
GRANT ALL PRIVILEGES ON DATABASE chatdb TO chat;
```

2. Apply schema:

```bash
psql -h <db-host> -U chat -d chatdb -f database/schema.sql
```

## Tables

- `chat_messages`: primary persistent message store (`message_id` idempotency key).
- `room_user_activity`: fast lookup for rooms user participated in.
- `failed_messages`: dead letter queue for DB write failures.
