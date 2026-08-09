CREATE TABLE menu_categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NULL,
    display_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_menu_categories_name UNIQUE (name),
    CONSTRAINT chk_menu_categories_display_order CHECK (display_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE menu_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(2000) NULL,
    base_price DECIMAL(12,2) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    available_for_sale BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_menu_items_code UNIQUE (code),
    CONSTRAINT fk_menu_items_category FOREIGN KEY (category_id) REFERENCES menu_categories (id) ON DELETE RESTRICT,
    CONSTRAINT chk_menu_items_price CHECK (base_price >= 0),
    CONSTRAINT chk_menu_items_display_order CHECK (display_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE modifier_groups (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NULL,
    selection_type VARCHAR(20) NOT NULL,
    minimum_selections INT NOT NULL DEFAULT 0,
    maximum_selections INT NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_modifier_groups_name UNIQUE (name),
    CONSTRAINT chk_modifier_groups_type CHECK (selection_type IN ('SINGLE', 'MULTIPLE')),
    CONSTRAINT chk_modifier_groups_minimum CHECK (minimum_selections >= 0),
    CONSTRAINT chk_modifier_groups_maximum CHECK (maximum_selections BETWEEN 1 AND 20),
    CONSTRAINT chk_modifier_groups_range CHECK (minimum_selections <= maximum_selections),
    CONSTRAINT chk_modifier_groups_single CHECK (selection_type <> 'SINGLE' OR maximum_selections = 1),
    CONSTRAINT chk_modifier_groups_display_order CHECK (display_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE modifier_options (
    id BIGINT NOT NULL AUTO_INCREMENT,
    modifier_group_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    price_adjustment DECIMAL(12,2) NOT NULL DEFAULT 0,
    display_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_modifier_options_group_name UNIQUE (modifier_group_id, name),
    CONSTRAINT fk_modifier_options_group FOREIGN KEY (modifier_group_id) REFERENCES modifier_groups (id) ON DELETE RESTRICT,
    CONSTRAINT chk_modifier_options_price CHECK (price_adjustment >= 0),
    CONSTRAINT chk_modifier_options_display_order CHECK (display_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE menu_item_modifier_groups (
    menu_item_id BIGINT NOT NULL,
    modifier_group_id BIGINT NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (menu_item_id, modifier_group_id),
    CONSTRAINT fk_menu_item_modifier_item FOREIGN KEY (menu_item_id) REFERENCES menu_items (id) ON DELETE RESTRICT,
    CONSTRAINT fk_menu_item_modifier_group FOREIGN KEY (modifier_group_id) REFERENCES modifier_groups (id) ON DELETE RESTRICT,
    CONSTRAINT chk_menu_item_modifier_order CHECK (display_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_menu_categories_active_order ON menu_categories (active, display_order, name);
CREATE INDEX idx_menu_items_category_order ON menu_items (category_id, display_order, name);
CREATE INDEX idx_menu_items_flags ON menu_items (active, available_for_sale);
CREATE INDEX idx_modifier_groups_active_order ON modifier_groups (active, display_order, name);
CREATE INDEX idx_modifier_options_group_order ON modifier_options (modifier_group_id, display_order, name);
CREATE INDEX idx_menu_item_modifier_order ON menu_item_modifier_groups (menu_item_id, display_order);
