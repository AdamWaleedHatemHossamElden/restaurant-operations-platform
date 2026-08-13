CREATE TABLE payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_number VARCHAR(32) NOT NULL,
    order_id BIGINT NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    method VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    external_reference VARCHAR(120) NULL,
    received_at TIMESTAMP(6) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_payments_number UNIQUE (payment_number),
    CONSTRAINT uk_payments_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uk_payments_external_reference UNIQUE (external_reference),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payments_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_payments_method CHECK (method IN ('CASH', 'CARD', 'BANK_TRANSFER', 'OTHER')),
    CONSTRAINT chk_payments_status CHECK (status = 'SUCCEEDED'),
    CONSTRAINT chk_payments_amount CHECK (amount > 0),
    CONSTRAINT chk_payments_currency CHECK (currency = 'EUR')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE payment_reconciliations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    payment_id BIGINT NOT NULL,
    reconciliation_reference VARCHAR(120) NULL,
    reconciled_at TIMESTAMP(6) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_payment_reconciliations_payment UNIQUE (payment_id),
    CONSTRAINT fk_payment_reconciliations_payment FOREIGN KEY (payment_id)
        REFERENCES payments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_reconciliations_actor FOREIGN KEY (actor_user_id)
        REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE invoices (
    id BIGINT NOT NULL AUTO_INCREMENT,
    invoice_number VARCHAR(32) NOT NULL,
    order_id BIGINT NOT NULL,
    order_number_snapshot VARCHAR(32) NOT NULL,
    currency CHAR(3) NOT NULL,
    subtotal DECIMAL(12,2) NOT NULL,
    total DECIMAL(12,2) NOT NULL,
    paid_total DECIMAL(12,2) NOT NULL,
    issued_at TIMESTAMP(6) NOT NULL,
    actor_user_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_invoices_number UNIQUE (invoice_number),
    CONSTRAINT uk_invoices_order UNIQUE (order_id),
    CONSTRAINT fk_invoices_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoices_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_invoices_currency CHECK (currency = 'EUR'),
    CONSTRAINT chk_invoices_subtotal CHECK (subtotal >= 0),
    CONSTRAINT chk_invoices_total CHECK (total >= 0),
    CONSTRAINT chk_invoices_paid_total CHECK (paid_total = total)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE invoice_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    invoice_id BIGINT NOT NULL,
    source_order_item_id BIGINT NOT NULL,
    item_code VARCHAR(40) NOT NULL,
    item_name VARCHAR(160) NOT NULL,
    quantity INT NOT NULL,
    base_price DECIMAL(12,2) NOT NULL,
    unit_total DECIMAL(12,2) NOT NULL,
    line_total DECIMAL(12,2) NOT NULL,
    display_order INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_invoice_items_source UNIQUE (invoice_id, source_order_item_id),
    CONSTRAINT fk_invoice_items_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE RESTRICT,
    CONSTRAINT fk_invoice_items_order_item FOREIGN KEY (source_order_item_id)
        REFERENCES order_items (id) ON DELETE RESTRICT,
    CONSTRAINT chk_invoice_items_quantity CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT chk_invoice_items_base_price CHECK (base_price >= 0),
    CONSTRAINT chk_invoice_items_unit_total CHECK (unit_total >= 0),
    CONSTRAINT chk_invoice_items_line_total CHECK (line_total >= 0),
    CONSTRAINT chk_invoice_items_display_order CHECK (display_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE invoice_item_modifiers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    invoice_item_id BIGINT NOT NULL,
    group_name VARCHAR(120) NOT NULL,
    option_name VARCHAR(120) NOT NULL,
    price_adjustment DECIMAL(12,2) NOT NULL,
    display_order INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_invoice_item_modifiers_item FOREIGN KEY (invoice_item_id)
        REFERENCES invoice_items (id) ON DELETE RESTRICT,
    CONSTRAINT chk_invoice_modifier_price CHECK (price_adjustment >= 0),
    CONSTRAINT chk_invoice_modifier_order CHECK (display_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_payments_order_received ON payments (order_id, received_at, id);
CREATE INDEX idx_payments_received_method ON payments (received_at, method, id);
CREATE INDEX idx_payment_reconciliations_time ON payment_reconciliations (reconciled_at, id);
CREATE INDEX idx_invoices_issued ON invoices (issued_at, id);
CREATE INDEX idx_invoice_items_invoice_order ON invoice_items (invoice_id, display_order, id);
CREATE INDEX idx_invoice_modifiers_item_order
    ON invoice_item_modifiers (invoice_item_id, display_order, id);
