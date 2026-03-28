-- Assignment 3 persistence schema (PostgreSQL)

CREATE TABLE IF NOT EXISTS chat_messages (
    message_id TEXT PRIMARY KEY,
    room_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    username TEXT NOT NULL,
    message TEXT NOT NULL,
    message_type TEXT NOT NULL,
    message_ts TIMESTAMP NOT NULL,
    server_id TEXT,
    client_ip TEXT,
    ingested_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS room_user_activity (
    room_id TEXT NOT NULL,
    user_id TEXT NOT NULL,
    last_activity_ts TIMESTAMP NOT NULL,
    PRIMARY KEY (room_id, user_id)
);

CREATE TABLE IF NOT EXISTS failed_messages (
    message_id TEXT PRIMARY KEY,
    room_id TEXT,
    user_id TEXT,
    payload JSONB,
    failure_reason TEXT,
    failed_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Core query indexes
CREATE INDEX IF NOT EXISTS idx_chat_messages_room_ts
    ON chat_messages (room_id, message_ts);

CREATE INDEX IF NOT EXISTS idx_chat_messages_user_ts
    ON chat_messages (user_id, message_ts DESC);

CREATE INDEX IF NOT EXISTS idx_chat_messages_ts
    ON chat_messages (message_ts);

-- Analytics helper indexes
CREATE INDEX IF NOT EXISTS idx_chat_messages_room_user_ts
    ON chat_messages (room_id, user_id, message_ts DESC);
