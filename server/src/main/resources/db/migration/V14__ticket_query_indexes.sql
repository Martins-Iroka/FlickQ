CREATE INDEX IF NOT EXISTS idx_reservations_user_id_created_at ON reservations (user_id, created_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_showtime_seats_reservation_id ON showtime_seats (reservation_id);