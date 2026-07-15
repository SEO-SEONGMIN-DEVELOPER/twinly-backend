CREATE TYPE CONNECTION_TYPE AS ENUM (
    'WS',
    'SSE'
);

CREATE TABLE connection_tickets (
    id               BIGINT GENERATED ALWAYS AS IDENTITY,
    user_id          BIGINT NOT NULL,
    ticket           UUID NOT NULL,
    connection_type  CONNECTION_TYPE NOT NULL,
    expires_at       TIMESTAMPTZ NOT NULL,
    used_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT pk_connection_tickets PRIMARY KEY (id),
    CONSTRAINT fk_connection_tickets_user_id FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_connection_tickets_ticket UNIQUE (ticket)
);