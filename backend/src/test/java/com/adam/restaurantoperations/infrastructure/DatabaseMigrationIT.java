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
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("5");
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name IN "
                        + "('roles', 'users', 'user_roles', 'restaurants', 'audit_logs', 'refresh_tokens', "
                        + "'restaurant_tables', 'reservations', 'menu_categories', 'menu_items', "
                        + "'modifier_groups', 'modifier_options', 'menu_item_modifier_groups')",
                Integer.class);
        assertThat(tableCount).isEqualTo(13);
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
    }
}
