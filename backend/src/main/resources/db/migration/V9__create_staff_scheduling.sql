CREATE TABLE employees (
    id BIGINT NOT NULL AUTO_INCREMENT,
    employee_code VARCHAR(40) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(254) NULL,
    phone VARCHAR(40) NULL,
    default_operational_role VARCHAR(20) NOT NULL,
    employment_start_date DATE NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_employees_code UNIQUE (employee_code),
    CONSTRAINT chk_employees_role CHECK (
        default_operational_role IN ('HOST', 'WAITER', 'CASHIER', 'KITCHEN', 'INVENTORY', 'MANAGER', 'OTHER')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE employee_availability (
    id BIGINT NOT NULL AUTO_INCREMENT,
    employee_id BIGINT NOT NULL,
    start_at TIMESTAMP(6) NOT NULL,
    end_at TIMESTAMP(6) NOT NULL,
    notes VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_employee_availability_employee FOREIGN KEY (employee_id)
        REFERENCES employees (id) ON DELETE RESTRICT,
    CONSTRAINT chk_employee_availability_range CHECK (start_at < end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE shifts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    employee_id BIGINT NOT NULL,
    operational_role VARCHAR(20) NOT NULL,
    start_at TIMESTAMP(6) NOT NULL,
    end_at TIMESTAMP(6) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    notes VARCHAR(1000) NULL,
    completed_at TIMESTAMP(6) NULL,
    cancelled_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_shifts_employee FOREIGN KEY (employee_id)
        REFERENCES employees (id) ON DELETE RESTRICT,
    CONSTRAINT chk_shifts_role CHECK (
        operational_role IN ('HOST', 'WAITER', 'CASHIER', 'KITCHEN', 'INVENTORY', 'MANAGER', 'OTHER')
    ),
    CONSTRAINT chk_shifts_status CHECK (status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_shifts_range CHECK (start_at < end_at),
    CONSTRAINT chk_shifts_terminal_time CHECK (
        (status = 'SCHEDULED' AND completed_at IS NULL AND cancelled_at IS NULL)
        OR (status = 'COMPLETED' AND completed_at IS NOT NULL AND cancelled_at IS NULL)
        OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND completed_at IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_employees_active_name
    ON employees (active, last_name, first_name, id);
CREATE INDEX idx_employee_availability_employee_time
    ON employee_availability (employee_id, start_at, end_at, id);
CREATE INDEX idx_shifts_employee_time
    ON shifts (employee_id, start_at, end_at, status, id);
CREATE INDEX idx_shifts_week
    ON shifts (start_at, end_at, operational_role, status, id);
