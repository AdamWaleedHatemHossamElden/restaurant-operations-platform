CREATE TABLE kitchen_tickets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    started_at TIMESTAMP(6) NULL,
    ready_at TIMESTAMP(6) NULL,
    cancelled_at TIMESTAMP(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_kitchen_tickets_order UNIQUE (order_id),
    CONSTRAINT fk_kitchen_tickets_order FOREIGN KEY (order_id)
        REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT chk_kitchen_tickets_status CHECK (
        status IN ('QUEUED', 'PREPARING', 'READY', 'CANCELLED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE kitchen_ticket_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    kitchen_ticket_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    started_at TIMESTAMP(6) NULL,
    ready_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_kitchen_items_ticket FOREIGN KEY (kitchen_ticket_id)
        REFERENCES kitchen_tickets (id) ON DELETE RESTRICT,
    CONSTRAINT fk_kitchen_items_order_item FOREIGN KEY (order_item_id)
        REFERENCES order_items (id) ON DELETE RESTRICT,
    CONSTRAINT uk_kitchen_items_order_item UNIQUE (order_item_id),
    CONSTRAINT uk_kitchen_items_ticket_order_item UNIQUE (kitchen_ticket_id, order_item_id),
    CONSTRAINT chk_kitchen_items_status CHECK (status IN ('QUEUED', 'PREPARING', 'READY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_kitchen_tickets_status_created ON kitchen_tickets (status, created_at, id);
CREATE INDEX idx_kitchen_tickets_order ON kitchen_tickets (order_id);
CREATE INDEX idx_kitchen_items_ticket_status ON kitchen_ticket_items (kitchen_ticket_id, status, id);

-- Preserve service continuity for active development data that was submitted before V7 existed.
INSERT INTO kitchen_tickets (order_id, status, created_at, updated_at)
SELECT id, 'QUEUED', COALESCE(submitted_at, created_at), COALESCE(submitted_at, updated_at)
FROM orders
WHERE status = 'SUBMITTED';

INSERT INTO kitchen_ticket_items (kitchen_ticket_id, order_item_id, status, created_at, updated_at)
SELECT ticket.id, item.id, 'QUEUED', ticket.created_at, ticket.created_at
FROM kitchen_tickets ticket
JOIN order_items item ON item.order_id = ticket.order_id;
