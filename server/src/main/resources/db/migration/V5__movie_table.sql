CREATE TABLE IF NOT EXISTS movies(
                                     id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    poster_url TEXT NOT NULL,
    duration INT NOT NULL,
    released_date DATE NOT NULL,
    created_at TIMESTAMP(0) WITH TIME ZONE NOT NULL DEFAULT NOW()
);