CREATE TABLE IF NOT EXISTS seats
(
    id          BIGSERIAL PRIMARY KEY,
    room_id     BIGINT NOT NULL,
    row_label   TEXT   NOT NULL,
    seat_number INT    NOT NULL,

    UNIQUE (room_id, row_label, seat_number),
    FOREIGN KEY (room_id) REFERENCES rooms (id) ON DELETE CASCADE
);