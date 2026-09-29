CREATE TYPE device_platform AS ENUM ('ANDROID', 'IOS');

CREATE TABLE IF NOT EXISTS device_tokens(
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token TEXT NOT NULL UNIQUE,
    platform device_platform NOT NULL,
    app_version VARCHAR(32) NOT NULL,
    locale VARCHAR(35) NOT NULL,
    created_at TIMESTAMP(0) WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMP(0) WITH TIME ZONE NOT NULL DEFAULT NOW(),
    invalidated_at TIMESTAMP(0) WITH TIME ZONE,

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_device_tokens_token_length CHECK (length(token) BETWEEN 20 AND 4096)
);

CREATE INDEX idx_device_tokens_active_user ON device_tokens (user_id) WHERE invalidated_at IS NULL;
