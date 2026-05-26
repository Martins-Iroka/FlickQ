CREATE TABLE IF NOT EXISTS movie_genre(
    movie_id BIGINT NOT NULL,
    genre_id BIGINT NOT NULL,

    PRIMARY KEY (movie_id, genre_id),
    FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE,
    FOREIGN KEY (genre_id) REFERENCES genres(id) ON DELETE CASCADE
);