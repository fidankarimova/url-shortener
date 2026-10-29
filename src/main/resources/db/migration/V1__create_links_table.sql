CREATE TABLE links (
                       id            BIGSERIAL    PRIMARY KEY,
                       short_code    VARCHAR(16)  NOT NULL,
                       original_url  TEXT         NOT NULL,
                       created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
                       expires_at    TIMESTAMPTZ,
                       click_count   BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX ux_links_short_code ON links (short_code);