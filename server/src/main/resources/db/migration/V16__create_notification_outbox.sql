CREATE TYPE notification_type AS ENUM ('MOVIE_ADDED', 'RESERVATION_HELD', 'BOOKING_CONFIRMED', 'TOPIC_SUBSCRIBE',
'TOPIC_UNSUBSCRIBE');

CREATE TYPE notification_target_kind AS ENUM ('TOPIC', 'USER', 'TOKENS');

CREATE TYPE notification_status AS ENUM ('PENDING', 'IN_FLIGHT', 'SENT', 'DEAD', 'EXPIRED');

CREATE TABLE IF NOT EXISTS notification_outbox(
    id BIGSERIAL PRIMARY KEY,
    type notification_type NOT NULL,
    idempotency_key TEXT UNIQUE NOT NULL,
    target_kind notification_target_kind NOT NULL,
    user_id BIGINT NULL,
    topic VARCHAR(255) NULL,
    payload JSONB NOT NULL,
    pending_tokens JSONB NULL,
    status notification_status NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(0) WITH TIME ZONE NOT NULL DEFAULT NOW(),
    locked_by VARCHAR(64) NULL,
    locked_until TIMESTAMP(0) WITH TIME ZONE NULL,
    expires_at TIMESTAMP(0) WITH TIME ZONE NULL,
    last_error TEXT NULL,
    trace_parent VARCHAR(64) NULL,
    created_at TIMESTAMP(0) WITH TIME ZONE NOT NULL DEFAULT NOW(),
    sent_at TIMESTAMP(0) WITH TIME ZONE NULL,

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_outbox_attempts CHECK (attempts >= 0),
    CONSTRAINT chk_outbox_target CHECK (
        (target_kind = 'TOPIC' AND topic IS NOT NULL AND user_id IS NULL AND pending_tokens IS NULL)
        OR (target_kind = 'USER' AND user_id IS NOT NULL AND topic IS NULL)
        OR (target_kind = 'TOKENS' AND pending_tokens IS NOT NULL)
    ),
    CONSTRAINT chk_outbox_lease CHECK (
        (status = 'IN_FLIGHT') = (locked_by IS NOT NULL AND locked_until IS NOT NULL)
    )
);

CREATE INDEX idx_outbox_due ON notification_outbox (next_attempt_at) WHERE status = 'PENDING';

CREATE INDEX idx_outbox_lease ON notification_outbox (locked_until) WHERE status = 'IN_FLIGHT';

CREATE INDEX idx_outbox_finished ON notification_outbox (created_at) WHERE status IN ('SENT', 'EXPIRED', 'DEAD');