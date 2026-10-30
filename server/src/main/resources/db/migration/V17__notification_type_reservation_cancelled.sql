-- Forward-only: Postgres can't drop an enum value. Rollback = leave the value unused and delete
-- RESERVATION_CANCELLED rows from notification_outbox before deploying an older build.
ALTER TYPE notification_type ADD VALUE IF NOT EXISTS 'RESERVATION_CANCELLED';
