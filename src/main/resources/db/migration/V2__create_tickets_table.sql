CREATE TABLE tickets (
    id UUID PRIMARY KEY,
    requester_id UUID NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(5000) NOT NULL,
    status VARCHAR(50) NOT NULL,
    priority VARCHAR(50) NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_tickets_requester FOREIGN KEY (requester_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_tickets_title_length CHECK (char_length(title) BETWEEN 1 AND 200),
    CONSTRAINT chk_tickets_description_length CHECK (char_length(description) BETWEEN 1 AND 5000),
    CONSTRAINT chk_tickets_status CHECK (status IN ('OPEN')),
    CONSTRAINT chk_tickets_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT uk_tickets_requester_idempotency UNIQUE (requester_id, idempotency_key)
);

CREATE INDEX idx_tickets_requester_id ON tickets (requester_id);
