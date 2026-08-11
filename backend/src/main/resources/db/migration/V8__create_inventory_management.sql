CREATE TABLE inventory_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    unit VARCHAR(20) NOT NULL,
    reorder_threshold DECIMAL(14,3) NOT NULL DEFAULT 0.000,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_inventory_items_code UNIQUE (code),
    CONSTRAINT uk_inventory_items_name UNIQUE (name),
    CONSTRAINT chk_inventory_items_unit CHECK (unit IN ('GRAM', 'MILLILITER', 'UNIT')),
    CONSTRAINT chk_inventory_items_threshold CHECK (reorder_threshold >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE stock_movements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    inventory_item_id BIGINT NOT NULL,
    movement_type VARCHAR(20) NOT NULL,
    quantity DECIMAL(14,3) NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actor_user_id BIGINT NULL,
    reference_type VARCHAR(40) NULL,
    reference_id BIGINT NULL,
    source_key VARCHAR(160) NULL,
    reason VARCHAR(500) NULL,
    unit_cost DECIMAL(12,4) NULL,
    total_cost DECIMAL(14,4) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_stock_movements_item FOREIGN KEY (inventory_item_id)
        REFERENCES inventory_items (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_movements_actor FOREIGN KEY (actor_user_id)
        REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT uk_stock_movements_source UNIQUE (source_key),
    CONSTRAINT chk_stock_movements_type CHECK (
        movement_type IN ('RECEIPT', 'USAGE', 'WASTE', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT')
    ),
    CONSTRAINT chk_stock_movements_quantity CHECK (quantity > 0),
    CONSTRAINT chk_stock_movements_cost CHECK (
        (unit_cost IS NULL AND total_cost IS NULL)
        OR (unit_cost >= 0 AND total_cost >= 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recipes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    menu_item_id BIGINT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_recipes_menu_item UNIQUE (menu_item_id),
    CONSTRAINT fk_recipes_menu_item FOREIGN KEY (menu_item_id)
        REFERENCES menu_items (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recipe_ingredients (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipe_id BIGINT NOT NULL,
    inventory_item_id BIGINT NOT NULL,
    quantity DECIMAL(14,3) NOT NULL,
    display_order INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_recipe_ingredients_recipe FOREIGN KEY (recipe_id)
        REFERENCES recipes (id) ON DELETE RESTRICT,
    CONSTRAINT fk_recipe_ingredients_item FOREIGN KEY (inventory_item_id)
        REFERENCES inventory_items (id) ON DELETE RESTRICT,
    CONSTRAINT uk_recipe_ingredients_item UNIQUE (recipe_id, inventory_item_id),
    CONSTRAINT uk_recipe_ingredients_order UNIQUE (recipe_id, display_order),
    CONSTRAINT chk_recipe_ingredients_quantity CHECK (quantity > 0),
    CONSTRAINT chk_recipe_ingredients_order CHECK (display_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE modifier_option_ingredients (
    id BIGINT NOT NULL AUTO_INCREMENT,
    modifier_option_id BIGINT NOT NULL,
    inventory_item_id BIGINT NOT NULL,
    quantity DECIMAL(14,3) NOT NULL,
    display_order INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_modifier_ingredients_option FOREIGN KEY (modifier_option_id)
        REFERENCES modifier_options (id) ON DELETE RESTRICT,
    CONSTRAINT fk_modifier_ingredients_item FOREIGN KEY (inventory_item_id)
        REFERENCES inventory_items (id) ON DELETE RESTRICT,
    CONSTRAINT uk_modifier_ingredients_item UNIQUE (modifier_option_id, inventory_item_id),
    CONSTRAINT uk_modifier_ingredients_order UNIQUE (modifier_option_id, display_order),
    CONSTRAINT chk_modifier_ingredients_quantity CHECK (quantity > 0),
    CONSTRAINT chk_modifier_ingredients_order CHECK (display_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE suppliers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    contact_name VARCHAR(160) NULL,
    email VARCHAR(254) NULL,
    phone VARCHAR(40) NULL,
    notes VARCHAR(1000) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_suppliers_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE supplier_inventory_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    supplier_id BIGINT NOT NULL,
    inventory_item_id BIGINT NOT NULL,
    supplier_item_code VARCHAR(80) NULL,
    unit_cost DECIMAL(12,4) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_supplier_items_supplier FOREIGN KEY (supplier_id)
        REFERENCES suppliers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_supplier_items_inventory FOREIGN KEY (inventory_item_id)
        REFERENCES inventory_items (id) ON DELETE RESTRICT,
    CONSTRAINT uk_supplier_items_pair UNIQUE (supplier_id, inventory_item_id),
    CONSTRAINT chk_supplier_items_cost CHECK (unit_cost >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE purchase_orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    purchase_order_number VARCHAR(32) NOT NULL,
    supplier_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    notes VARCHAR(1000) NULL,
    subtotal DECIMAL(14,4) NOT NULL DEFAULT 0.0000,
    total DECIMAL(14,4) NOT NULL DEFAULT 0.0000,
    ordered_at TIMESTAMP(6) NULL,
    received_at TIMESTAMP(6) NULL,
    cancelled_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_purchase_orders_number UNIQUE (purchase_order_number),
    CONSTRAINT fk_purchase_orders_supplier FOREIGN KEY (supplier_id)
        REFERENCES suppliers (id) ON DELETE RESTRICT,
    CONSTRAINT chk_purchase_orders_status CHECK (
        status IN ('DRAFT', 'ORDERED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED')
    ),
    CONSTRAINT chk_purchase_orders_totals CHECK (subtotal >= 0 AND total >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE purchase_order_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    purchase_order_id BIGINT NOT NULL,
    inventory_item_id BIGINT NOT NULL,
    inventory_code_snapshot VARCHAR(40) NOT NULL,
    inventory_name_snapshot VARCHAR(160) NOT NULL,
    unit_snapshot VARCHAR(20) NOT NULL,
    ordered_quantity DECIMAL(14,3) NOT NULL,
    received_quantity DECIMAL(14,3) NOT NULL DEFAULT 0.000,
    unit_cost_snapshot DECIMAL(12,4) NOT NULL,
    line_total DECIMAL(14,4) NOT NULL,
    display_order INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT fk_purchase_order_items_order FOREIGN KEY (purchase_order_id)
        REFERENCES purchase_orders (id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_order_items_inventory FOREIGN KEY (inventory_item_id)
        REFERENCES inventory_items (id) ON DELETE RESTRICT,
    CONSTRAINT uk_purchase_order_items_inventory UNIQUE (purchase_order_id, inventory_item_id),
    CONSTRAINT uk_purchase_order_items_order UNIQUE (purchase_order_id, display_order),
    CONSTRAINT chk_purchase_order_items_quantities CHECK (
        ordered_quantity > 0 AND received_quantity >= 0 AND received_quantity <= ordered_quantity
    ),
    CONSTRAINT chk_purchase_order_items_cost CHECK (unit_cost_snapshot >= 0 AND line_total >= 0),
    CONSTRAINT chk_purchase_order_items_display CHECK (display_order >= 0),
    CONSTRAINT chk_purchase_order_items_unit CHECK (unit_snapshot IN ('GRAM', 'MILLILITER', 'UNIT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_stock_movements_item_time ON stock_movements (inventory_item_id, occurred_at, id);
CREATE INDEX idx_stock_movements_reference ON stock_movements (reference_type, reference_id);
CREATE INDEX idx_inventory_items_active_name ON inventory_items (active, name, id);
CREATE INDEX idx_supplier_items_supplier ON supplier_inventory_items (supplier_id, active, inventory_item_id);
CREATE INDEX idx_purchase_orders_status_created ON purchase_orders (status, created_at, id);
CREATE INDEX idx_purchase_order_items_order ON purchase_order_items (purchase_order_id, display_order, id);
