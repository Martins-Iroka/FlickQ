CREATE TYPE payment_status AS ENUM (
    'INITIATED',
    'PENDING',
    'SUCCESS',
    'FAILED',
    'ABANDONED',
    'REFUND_PENDING',
    'REFUNDED',
    'REFUND_FAILED'
    );

CREATE TABLE IF NOT EXISTS payments
(
    id                      BIGSERIAL PRIMARY KEY,
    reservation_id          BIGINT                      NOT NULL,
    user_id                 BIGINT                      NOT NULL,
    reference               VARCHAR(100)                NOT NULL UNIQUE,
    amount                  BIGINT                      NOT NULL,
    currency                VARCHAR(8)                  NOT NULL DEFAULT 'NGN',
    status                  payment_status              NOT NULL,
    authorization_url       TEXT,
    access_code TEXT,
    paystack_transaction_id VARCHAR(100),
    gateway_response        TEXT,
    paid_at                 TIMESTAMP(0) WITH TIME ZONE,
    refunded_at             TIMESTAMP(0) WITH TIME ZONE,
    created_at              TIMESTAMP(0) WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP(0) WITH TIME ZONE NOT NULL DEFAULT NOW(),

    FOREIGN KEY (reservation_id) REFERENCES reservations (id),
    FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_payments_reservation_id ON payments (reservation_id);
CREATE INDEX idx_payments_status ON payments (status);
