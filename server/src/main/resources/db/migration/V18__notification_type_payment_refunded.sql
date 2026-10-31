-- Forward-only: Postgres can't drop an enum value. Rollback = leave the value unused and delete
-- PAYMENT_REFUNDED rows from notification_outbox before deploying an older build.
ALTER TYPE notification_type ADD VALUE IF NOT EXISTS 'PAYMENT_REFUNDED';
