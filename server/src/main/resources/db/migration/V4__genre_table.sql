CREATE TABLE IF NOT EXISTS genres(
                                     id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);