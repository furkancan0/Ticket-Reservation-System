-- ShedLock table (required for distributed scheduler locking)
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);


CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100),
    last_name     VARCHAR(100),
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN'))
);


CREATE TABLE events (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    venue       VARCHAR(255) NOT NULL,
    event_date  TIMESTAMP    NOT NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);


CREATE TABLE seats (
    id         UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id   UUID           NOT NULL REFERENCES events(id),
    section    VARCHAR(50)    NOT NULL,
    row_label  VARCHAR(10)    NOT NULL,
    seat_num   VARCHAR(10)    NOT NULL,
    price      NUMERIC(10, 2) NOT NULL,
    status     VARCHAR(20)    NOT NULL DEFAULT 'AVAILABLE',
    version    BIGINT         NOT NULL DEFAULT 0,   --optimistic lock counter
    created_at TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_seats UNIQUE (event_id, section, row_label, seat_num),
    CONSTRAINT chk_seat_status CHECK (status IN ('AVAILABLE', 'PENDING', 'CONFIRMED'))
);

CREATE INDEX idx_seats_event_status ON seats (event_id, status);


CREATE TABLE seat_holds (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    seat_id     UUID         NOT NULL REFERENCES seats(id),
    user_id     UUID         NOT NULL REFERENCES users(id),
    hold_token  VARCHAR(64)  NOT NULL,
    held_until  TIMESTAMP    NOT NULL,
    expired     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hold_token UNIQUE (hold_token)
);

CREATE UNIQUE INDEX uq_active_hold_per_seat
    ON seat_holds (seat_id)
    WHERE expired = FALSE;

CREATE INDEX idx_seat_holds_expiry ON seat_holds (held_until) WHERE expired = FALSE;

CREATE TABLE discount_codes (
    id            UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(50)    NOT NULL,
    discount_type VARCHAR(20)    NOT NULL,
    value         NUMERIC(10, 2) NOT NULL,
    max_uses      INT            NOT NULL DEFAULT 1,
    used_count    INT            NOT NULL DEFAULT 0,
    valid_from    TIMESTAMP      NOT NULL,
    valid_until   TIMESTAMP      NOT NULL,
    active        BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_discount_code UNIQUE (code),
    CONSTRAINT chk_discount_type  CHECK (discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')),
    CONSTRAINT chk_used_lte_max   CHECK (used_count <= max_uses)
);

CREATE TABLE orders (
    id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID           NOT NULL REFERENCES users(id),
    seat_id             UUID           NOT NULL REFERENCES seats(id),
    discount_code_id    UUID           REFERENCES discount_codes(id),
    status              VARCHAR(30)    NOT NULL DEFAULT 'PENDING',
    original_amount     NUMERIC(10, 2) NOT NULL,
    discount_amount     NUMERIC(10, 2) NOT NULL DEFAULT 0,
    total_amount        NUMERIC(10, 2) NOT NULL,
    payment_provider    VARCHAR(20),
    external_payment_id VARCHAR(255),
    failure_reason      TEXT,
    created_at          TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_order_status CHECK (status IN (
        'PENDING', 'CONFIRMED', 'PAYMENT_FAILED', 'CANCELLED', 'EXPIRED'
    ))
);

CREATE INDEX idx_orders_user    ON orders (user_id);
CREATE INDEX idx_orders_seat    ON orders (seat_id);
CREATE INDEX idx_orders_status  ON orders (status);


CREATE TABLE payment_transactions (
    id                      UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id                UUID           NOT NULL REFERENCES orders(id),
    provider                VARCHAR(20)    NOT NULL,
    provider_transaction_id VARCHAR(255),
    amount                  NUMERIC(10, 2) NOT NULL,
    status                  VARCHAR(30)    NOT NULL,
    error_message           TEXT,
    raw_response            TEXT,
    created_at              TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payment_tx_order ON payment_transactions (order_id);
