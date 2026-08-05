CREATE TABLE reservations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reservation_code VARCHAR(24) NOT NULL,
    guest_name VARCHAR(160) NOT NULL,
    guest_phone VARCHAR(32) NOT NULL,
    guest_email VARCHAR(320) NULL,
    party_size INT NOT NULL,
    start_at TIMESTAMP(6) NOT NULL,
    duration_minutes INT NOT NULL,
    restaurant_table_id BIGINT NULL,
    status VARCHAR(32) NOT NULL,
    notes VARCHAR(2000) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_reservations_code UNIQUE (reservation_code),
    CONSTRAINT fk_reservations_table FOREIGN KEY (restaurant_table_id)
        REFERENCES restaurant_tables (id) ON DELETE RESTRICT,
    CONSTRAINT chk_reservations_party_size CHECK (party_size BETWEEN 1 AND 100),
    CONSTRAINT chk_reservations_duration CHECK (duration_minutes BETWEEN 15 AND 480),
    CONSTRAINT chk_reservations_status CHECK (
        status IN ('PENDING', 'CONFIRMED', 'SEATED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_reservations_start_at ON reservations (start_at);
CREATE INDEX idx_reservations_status_start ON reservations (status, start_at);
CREATE INDEX idx_reservations_table_start ON reservations (restaurant_table_id, start_at);
