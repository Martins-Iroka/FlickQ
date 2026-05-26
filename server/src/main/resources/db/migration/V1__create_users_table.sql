CREATE EXTENSION IF NOT EXISTS citext;

CREATE TYPE role_data AS ENUM ('USER', 'ADMIN');

CREATE TABLE IF NOT EXISTS users(
                                    id BIGSERIAL PRIMARY KEY,
    email citext UNIQUE NOT NULL,
    password TEXT NOT NULL,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    role role_data NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP(0) WITH TIME ZONE NOT NULL DEFAULT NOW()
);