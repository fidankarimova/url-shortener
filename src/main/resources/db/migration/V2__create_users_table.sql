CREATE TABLE users (
                       id            BIGSERIAL    PRIMARY KEY,
                       email         VARCHAR(255) NOT NULL,
                       password_hash VARCHAR(100) NOT NULL,
                       created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_users_email ON users (email);

ALTER TABLE links ADD COLUMN user_id BIGINT REFERENCES users (id);
CREATE INDEX ix_links_user_id ON links (user_id);