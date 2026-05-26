CREATE TABLE IF NOT EXISTS rooms
(
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    rows INT          NOT NULL,
    cols INT          NOT NULL
);