CREATE TYPE status_type AS ENUM ('PENDING', 'CONFIRMED', 'CANCELLED');

CREATE TABLE IF NOT EXISTS reservations(
                                           id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    showtime_id BIGINT NOT NULL,
    status status_type NOT NULL,
    total_amount BIGINT NOT NULL,
    created_at TIMESTAMP(0) WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,

    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (showtime_id) REFERENCES show_times(id)
);