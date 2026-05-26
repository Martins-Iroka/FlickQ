CREATE TYPE seat_status AS ENUM ('AVAILABLE', 'HELD', 'BOOKED');

CREATE TABLE IF NOT EXISTS showtime_seats(
                                             id BIGSERIAL PRIMARY KEY,
    showtime_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    reservation_id BIGINT NULL,
    status seat_status NOT NULL DEFAULT 'AVAILABLE',

    UNIQUE (showtime_id, seat_id), -- the anti overbooking guarantee
    FOREIGN KEY (showtime_id) REFERENCES show_times(id),
    FOREIGN KEY (seat_id) REFERENCES seats(id),
    FOREIGN KEY (reservation_id) REFERENCES reservations(id)
);