CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_number VARCHAR(32) NOT NULL,
    restaurant_table_id BIGINT NOT NULL,
    reservation_id BIGINT NULL,
    status VARCHAR(20) NOT NULL,
    notes VARCHAR(2000) NULL,
    subtotal DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    submitted_at TIMESTAMP(6) NULL,
    completed_at TIMESTAMP(6) NULL,
    cancelled_at TIMESTAMP(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_orders_order_number UNIQUE (order_number),
    CONSTRAINT fk_orders_table FOREIGN KEY (restaurant_table_id)
        REFERENCES restaurant_tables (id) ON DELETE RESTRICT,
    CONSTRAINT fk_orders_reservation FOREIGN KEY (reservation_id)
        REFERENCES reservations (id) ON DELETE RESTRICT,
    CONSTRAINT chk_orders_status CHECK (status IN ('OPEN', 'SUBMITTED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_orders_subtotal CHECK (subtotal >= 0),
    CONSTRAINT chk_orders_total CHECK (total >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE order_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    menu_item_id BIGINT NOT NULL,
    item_code_snapshot VARCHAR(40) NOT NULL,
    item_name_snapshot VARCHAR(160) NOT NULL,
    base_price_snapshot DECIMAL(12,2) NOT NULL,
    quantity INT NOT NULL,
    notes VARCHAR(1000) NULL,
    unit_total_snapshot DECIMAL(12,2) NOT NULL,
    line_total DECIMAL(12,2) NOT NULL,
    display_order INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_menu_item FOREIGN KEY (menu_item_id)
        REFERENCES menu_items (id) ON DELETE RESTRICT,
    CONSTRAINT uk_order_items_display_order UNIQUE (order_id, display_order),
    CONSTRAINT chk_order_items_quantity CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT chk_order_items_base_price CHECK (base_price_snapshot >= 0),
    CONSTRAINT chk_order_items_unit_total CHECK (unit_total_snapshot >= 0),
    CONSTRAINT chk_order_items_line_total CHECK (line_total >= 0),
    CONSTRAINT chk_order_items_display_order CHECK (display_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE order_item_modifiers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_item_id BIGINT NOT NULL,
    modifier_group_id BIGINT NOT NULL,
    modifier_option_id BIGINT NOT NULL,
    group_name_snapshot VARCHAR(120) NOT NULL,
    option_name_snapshot VARCHAR(120) NOT NULL,
    price_adjustment_snapshot DECIMAL(12,2) NOT NULL,
    display_order INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_order_item_modifiers_item FOREIGN KEY (order_item_id)
        REFERENCES order_items (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_item_modifiers_group FOREIGN KEY (modifier_group_id)
        REFERENCES modifier_groups (id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_item_modifiers_option FOREIGN KEY (modifier_option_id)
        REFERENCES modifier_options (id) ON DELETE RESTRICT,
    CONSTRAINT uk_order_item_modifier_option UNIQUE (order_item_id, modifier_option_id),
    CONSTRAINT chk_order_item_modifier_price CHECK (price_adjustment_snapshot >= 0),
    CONSTRAINT chk_order_item_modifier_order CHECK (display_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE order_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    from_status VARCHAR(20) NULL,
    to_status VARCHAR(20) NOT NULL,
    changed_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    changed_by_user_id BIGINT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_order_history_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_history_user FOREIGN KEY (changed_by_user_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT chk_order_history_from_status CHECK (
        from_status IS NULL OR from_status IN ('OPEN', 'SUBMITTED', 'COMPLETED', 'CANCELLED')
    ),
    CONSTRAINT chk_order_history_to_status CHECK (
        to_status IN ('OPEN', 'SUBMITTED', 'COMPLETED', 'CANCELLED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_orders_status_created ON orders (status, created_at);
CREATE INDEX idx_orders_table_created ON orders (restaurant_table_id, created_at);
CREATE INDEX idx_orders_reservation ON orders (reservation_id);
CREATE INDEX idx_order_items_order ON order_items (order_id, display_order, id);
CREATE INDEX idx_order_item_modifiers_item ON order_item_modifiers (order_item_id, display_order, id);
CREATE INDEX idx_order_history_order ON order_status_history (order_id, changed_at, id);
