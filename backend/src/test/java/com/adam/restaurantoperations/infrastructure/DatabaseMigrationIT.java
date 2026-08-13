package com.adam.restaurantoperations.infrastructure;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class DatabaseMigrationIT {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("restaurant_operations")
            .withUsername("restaurant_user")
            .withPassword("integration_test_password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.auth.jwt-secret", () -> "integration-test-only-jwt-key-with-at-least-32-bytes");
        registry.add("app.frontend-origin", () -> "http://localhost:5173");
    }

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void startsMySqlAndAppliesAllMigrations() {
        assertThat(MYSQL.isRunning()).isTrue();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("11");
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name IN "
                        + "('roles', 'users', 'user_roles', 'restaurants', 'audit_logs', 'refresh_tokens', "
                        + "'restaurant_tables', 'reservations', 'menu_categories', 'menu_items', "
                        + "'modifier_groups', 'modifier_options', 'menu_item_modifier_groups', 'orders', "
                        + "'order_items', 'order_item_modifiers', 'order_status_history', "
                        + "'kitchen_tickets', 'kitchen_ticket_items', 'inventory_items', 'stock_movements', "
                        + "'recipes', 'recipe_ingredients', 'modifier_option_ingredients', 'suppliers', "
                        + "'supplier_inventory_items', 'purchase_orders', 'purchase_order_items', "
                        + "'employees', 'employee_availability', 'shifts', 'payments', "
                        + "'payment_reconciliations', 'invoices', 'invoice_items', "
                        + "'invoice_item_modifiers')",
                Integer.class);
        assertThat(tableCount).isEqualTo(36);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'restaurant_tables' "
                        + "AND column_name IN ('table_number', 'display_name', 'capacity', 'section', "
                        + "'status', 'active', 'created_at', 'updated_at', 'version')",
                Integer.class)).isEqualTo(9);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'reservations' "
                        + "AND column_name IN ('reservation_code', 'guest_name', 'guest_phone', 'guest_email', "
                        + "'party_size', 'start_at', 'duration_minutes', 'restaurant_table_id', 'status', "
                        + "'notes', 'created_at', 'updated_at', 'version')",
                Integer.class)).isEqualTo(13);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'menu_items' "
                        + "AND column_name IN ('category_id', 'code', 'name', 'base_price', 'active', "
                        + "'available_for_sale', 'version')",
                Integer.class)).isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE table_schema = DATABASE() AND table_name IN "
                        + "('menu_categories', 'menu_items', 'modifier_groups', 'modifier_options', "
                        + "'menu_item_modifier_groups') AND constraint_type IN "
                        + "('UNIQUE', 'FOREIGN KEY', 'CHECK')",
                Integer.class)).isGreaterThanOrEqualTo(17);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'orders' "
                        + "AND column_name IN ('order_number', 'restaurant_table_id', 'reservation_id', "
                        + "'status', 'subtotal', 'total', 'submitted_at', 'completed_at', 'cancelled_at', 'version')",
                Integer.class)).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE table_schema = DATABASE() AND table_name IN "
                        + "('orders', 'order_items', 'order_item_modifiers', 'order_status_history') "
                        + "AND constraint_type IN ('UNIQUE', 'FOREIGN KEY', 'CHECK')",
                Integer.class)).isGreaterThanOrEqualTo(22);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'kitchen_tickets' "
                        + "AND column_name IN ('order_id', 'status', 'created_at', 'updated_at', "
                        + "'started_at', 'ready_at', 'cancelled_at', 'version')",
                Integer.class)).isEqualTo(8);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE table_schema = DATABASE() AND table_name IN "
                        + "('kitchen_tickets', 'kitchen_ticket_items') "
                        + "AND constraint_type IN ('UNIQUE', 'FOREIGN KEY', 'CHECK')",
                Integer.class)).isGreaterThanOrEqualTo(8);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND index_name IN "
                        + "('idx_orders_status_completed', 'idx_kitchen_tickets_created', "
                        + "'idx_stock_movements_occurred')",
                Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name IN "
                        + "('payments', 'payment_reconciliations', 'invoices', 'invoice_items', "
                        + "'invoice_item_modifiers')",
                Integer.class)).isEqualTo(47);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE table_schema = DATABASE() AND table_name IN "
                        + "('payments', 'payment_reconciliations', 'invoices', 'invoice_items', "
                        + "'invoice_item_modifiers') AND constraint_type IN "
                        + "('UNIQUE', 'FOREIGN KEY', 'CHECK')",
                Integer.class)).isGreaterThanOrEqualTo(24);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = 'stock_movements' "
                        + "AND column_name IN ('inventory_item_id', 'movement_type', 'quantity', 'occurred_at', "
                        + "'actor_user_id', 'reference_type', 'reference_id', 'source_key', 'unit_cost', "
                        + "'total_cost')",
                Integer.class)).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE table_schema = DATABASE() AND table_name IN "
                        + "('inventory_items', 'stock_movements', 'recipes', 'recipe_ingredients', "
                        + "'modifier_option_ingredients', 'suppliers', 'supplier_inventory_items', "
                        + "'purchase_orders', 'purchase_order_items') "
                        + "AND constraint_type IN ('UNIQUE', 'FOREIGN KEY', 'CHECK')",
                Integer.class)).isGreaterThanOrEqualTo(28);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name IN "
                        + "('employees', 'employee_availability', 'shifts')",
                Integer.class)).isEqualTo(32);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_constraints "
                        + "WHERE table_schema = DATABASE() AND table_name IN "
                        + "('employees', 'employee_availability', 'shifts') "
                        + "AND constraint_type IN ('UNIQUE', 'FOREIGN KEY', 'CHECK')",
                Integer.class)).isGreaterThanOrEqualTo(8);
    }
}
