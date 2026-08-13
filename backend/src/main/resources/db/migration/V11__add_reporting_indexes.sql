CREATE INDEX idx_orders_status_completed
    ON orders (status, completed_at, id);

CREATE INDEX idx_kitchen_tickets_created
    ON kitchen_tickets (created_at, status, id);

CREATE INDEX idx_stock_movements_occurred
    ON stock_movements (occurred_at, movement_type, inventory_item_id, id);
