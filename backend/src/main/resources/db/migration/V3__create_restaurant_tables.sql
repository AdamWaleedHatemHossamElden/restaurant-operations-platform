CREATE TABLE restaurant_tables (
    id BIGINT NOT NULL AUTO_INCREMENT,
    table_number VARCHAR(32) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    capacity INT NOT NULL,
    section VARCHAR(80) NOT NULL,
    status VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_restaurant_tables_table_number UNIQUE (table_number),
    CONSTRAINT chk_restaurant_tables_capacity CHECK (capacity > 0),
    CONSTRAINT chk_restaurant_tables_status CHECK (status IN ('AVAILABLE', 'OUT_OF_SERVICE'))
);

CREATE INDEX idx_restaurant_tables_active_status
    ON restaurant_tables (active, status);

CREATE INDEX idx_restaurant_tables_section
    ON restaurant_tables (section);
