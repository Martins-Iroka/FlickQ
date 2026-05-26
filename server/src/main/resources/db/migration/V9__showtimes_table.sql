CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TYPE showtime_status AS ENUM ('SCHEDULED', 'CANCELLED', 'COMPLETED');

CREATE TABLE IF NOT EXISTS show_times(
                                         id BIGSERIAL PRIMARY KEY,
    movie_id BIGINT NOT NULL,
                                         room_id BIGINT  NOT NULL,
                                         starts_at  TIMESTAMP(0) WITH TIME ZONE NOT NULL,
                                         ends_at    TIMESTAMP(0) WITH TIME ZONE NOT NULL,
                                         price   INTEGER NOT NULL,
    status showtime_status NOT NULL DEFAULT 'SCHEDULED',
                                         created_at TIMESTAMP(0) WITH TIME ZONE NOT NULL DEFAULT now(),
    EXCLUDE USING gist (room_id WITH =, tstzrange(starts_at, ends_at) WITH &&), -- no room overlap

    FOREIGN KEY (movie_id) REFERENCES movies(id),
    FOREIGN KEY (room_id) REFERENCES rooms(id)
);