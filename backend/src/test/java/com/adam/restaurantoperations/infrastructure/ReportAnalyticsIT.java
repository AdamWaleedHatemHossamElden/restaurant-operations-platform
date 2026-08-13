package com.adam.restaurantoperations.infrastructure;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ReportAnalyticsIT {
    private static final Instant FROM = Instant.parse("2030-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2030-01-02T00:00:00Z");

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("restaurant_operations")
            .withUsername("restaurant_user")
            .withPassword("integration_test_password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.auth.jwt-secret", () -> "integration-test-only-jwt-key-with-at-least-32-bytes");
        registry.add("app.frontend-origin", () -> "http://localhost:5173");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void reportsUseHalfOpenAuthoritativeDataRemainReadOnlyAndExportSafely() throws Exception {
        seedReportData();
        long auditBefore = count("audit_logs");

        report("overview")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedOrders").value(1))
                .andExpect(jsonPath("$.completedOrderValue").value("20.00"))
                .andExpect(jsonPath("$.paymentsReceived").value("20.00"))
                .andExpect(jsonPath("$.paymentCount").value(1))
                .andExpect(jsonPath("$.reconciledPaymentCount").value(1))
                .andExpect(jsonPath("$.reservations").value(1))
                .andExpect(jsonPath("$.readyKitchenTickets").value(1))
                .andExpect(jsonPath("$.scheduledStaffHours").value("10.00"));
        report("sales")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedOrders").value(1))
                .andExpect(jsonPath("$.series[0].amount").value("20.00"));
        report("menu-performance")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantitySold").value(2))
                .andExpect(jsonPath("$.items[0].completedOrderLineValue").value("20.00"));
        report("payments")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentsReceived").value("20.00"))
                .andExpect(jsonPath("$.reconciledAmount").value("20.00"))
                .andExpect(jsonPath("$.unreconciledAmount").value("0.00"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("OPAQUE"))));
        report("reservations")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plannedGuests").value(4))
                .andExpect(jsonPath("$.byStatus[0].key").value("CONFIRMED"));
        report("kitchen")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketsCreated").value(3))
                .andExpect(jsonPath("$.readyTickets").value(1))
                .andExpect(jsonPath("$.cancelledTickets").value(1))
                .andExpect(jsonPath("$.averagePreparationMinutes").value("30.00"));
        report("inventory")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movementCount").value(6))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[?(@.unit == 'GRAM')].netMovement")
                        .value(org.hamcrest.Matchers.contains("3.650")))
                .andExpect(jsonPath("$.items[?(@.unit == 'UNIT')].netMovement")
                        .value(org.hamcrest.Matchers.contains("3.000")));
        report("staff")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shiftCount").value(3))
                .andExpect(jsonPath("$.scheduledCount").value(1))
                .andExpect(jsonPath("$.completedCount").value(1))
                .andExpect(jsonPath("$.cancelledCount").value(1))
                .andExpect(jsonPath("$.scheduledHours").value("10.00"))
                .andExpect(jsonPath("$.completedShiftHours").value("8.00"));

        String menuCsv = mockMvc.perform(get("/api/v1/reports/exports/menu.csv")
                        .param("from", FROM.toString())
                        .param("to", TO.toString())
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(menuCsv).contains("\"'=SUM(1,2)\"").doesNotContain("guest@example.com");

        assertThat(count("audit_logs")).isEqualTo(auditBefore);
        assertThat(count("orders")).isEqualTo(5);
        assertThat(count("payments")).isEqualTo(2);
        assertThat(count("stock_movements")).isEqualTo(6);
    }

    private org.springframework.test.web.servlet.ResultActions report(String name) throws Exception {
        return mockMvc.perform(get("/api/v1/reports/" + name)
                .param("from", FROM.toString())
                .param("to", TO.toString())
                .with(adminJwt()));
    }

    private void seedReportData() {
        jdbc.update("INSERT INTO users (email, password_hash, display_name, enabled) VALUES (?, ?, ?, TRUE)",
                "reports-admin@example.com", "integration-hash", "Reports Admin");
        long actor = id("users", "email", "reports-admin@example.com");
        jdbc.update("INSERT INTO restaurant_tables "
                + "(table_number, display_name, capacity, section, status, active) "
                + "VALUES ('REP-1', 'Report Table', 4, 'Main', 'AVAILABLE', TRUE)");
        long table = id("restaurant_tables", "table_number", "REP-1");
        jdbc.update("INSERT INTO menu_categories (name, display_order, active) VALUES ('Reports', 0, TRUE)");
        long category = id("menu_categories", "name", "Reports");
        jdbc.update("INSERT INTO menu_items "
                + "(category_id, code, name, base_price, display_order, active, available_for_sale) "
                + "VALUES (?, 'REP-ITEM', 'Current Name', 10.00, 0, TRUE, TRUE)", category);
        long menuItem = id("menu_items", "code", "REP-ITEM");
        long completed = order(table, "REP-COMPLETED", "COMPLETED", FROM, "20.00");
        long open = order(table, "REP-OPEN", "OPEN", FROM.plusSeconds(3600), "999.00");
        long submitted = order(table, "REP-SUBMITTED", "SUBMITTED", FROM.plusSeconds(7200), "888.00");
        order(table, "REP-CANCELLED", "CANCELLED", FROM.plusSeconds(10_800), "777.00");
        order(table, "REP-TO-BOUNDARY", "COMPLETED", TO, "50.00");
        jdbc.update("INSERT INTO order_items "
                + "(order_id, menu_item_id, item_code_snapshot, item_name_snapshot, base_price_snapshot, "
                + "quantity, unit_total_snapshot, line_total, display_order) "
                + "VALUES (?, ?, 'REP-ITEM', '=SUM(1,2)', 10.00, 2, 10.00, 20.00, 0)",
                completed, menuItem);
        jdbc.update("UPDATE menu_items SET name = 'Renamed Current Item', base_price = 99.00 WHERE id = ?", menuItem);
        long orderItem = jdbc.queryForObject(
                "SELECT id FROM order_items WHERE order_id = ?", Long.class, completed);
        jdbc.update("INSERT INTO payments "
                + "(payment_number, order_id, idempotency_key, method, status, amount, currency, "
                + "external_reference, received_at, actor_user_id) "
                + "VALUES ('PAY-REPORT-IN', ?, 'report-idem-in', 'CARD', 'SUCCEEDED', 20.00, 'EUR', "
                + "'OPAQUE-REPORT-IN', ?, ?)", completed, timestamp(FROM), actor);
        long payment = id("payments", "payment_number", "PAY-REPORT-IN");
        jdbc.update("INSERT INTO payment_reconciliations "
                + "(payment_id, reconciliation_reference, reconciled_at, actor_user_id) VALUES (?, NULL, ?, ?)",
                payment, timestamp(FROM.plusSeconds(60)), actor);
        jdbc.update("INSERT INTO payments "
                + "(payment_number, order_id, idempotency_key, method, status, amount, currency, "
                + "external_reference, received_at, actor_user_id) "
                + "VALUES ('PAY-REPORT-OUT', ?, 'report-idem-out', 'CASH', 'SUCCEEDED', 50.00, 'EUR', "
                + "NULL, ?, ?)", completed, timestamp(TO), actor);
        jdbc.update("INSERT INTO invoices "
                + "(invoice_number, order_id, order_number_snapshot, currency, subtotal, total, paid_total, "
                + "issued_at, actor_user_id) VALUES ('INV-REPORT', ?, 'REP-COMPLETED', 'EUR', "
                + "20.00, 20.00, 20.00, ?, ?)", completed, timestamp(FROM.plusSeconds(120)), actor);
        jdbc.update("INSERT INTO reservations "
                + "(reservation_code, guest_name, guest_phone, guest_email, party_size, start_at, "
                + "duration_minutes, restaurant_table_id, status) "
                + "VALUES ('RSV-REPORT-IN', 'Private Guest', '000', 'guest@example.com', 4, ?, 60, ?, 'CONFIRMED')",
                timestamp(FROM), table);
        jdbc.update("INSERT INTO reservations "
                + "(reservation_code, guest_name, guest_phone, party_size, start_at, duration_minutes, status) "
                + "VALUES ('RSV-REPORT-OUT', 'Other Guest', '111', 2, ?, 60, 'PENDING')", timestamp(TO));
        jdbc.update("INSERT INTO kitchen_tickets "
                + "(order_id, status, created_at, updated_at, started_at, ready_at) "
                + "VALUES (?, 'READY', ?, ?, ?, ?)", completed, timestamp(FROM), timestamp(FROM.plusSeconds(1800)),
                timestamp(FROM.plusSeconds(60)), timestamp(FROM.plusSeconds(1800)));
        long readyTicket = jdbc.queryForObject(
                "SELECT id FROM kitchen_tickets WHERE order_id = ?", Long.class, completed);
        jdbc.update("INSERT INTO kitchen_ticket_items "
                + "(kitchen_ticket_id, order_item_id, status, created_at, updated_at, ready_at) "
                + "VALUES (?, ?, 'READY', ?, ?, ?)", readyTicket, orderItem, timestamp(FROM),
                timestamp(FROM.plusSeconds(1800)), timestamp(FROM.plusSeconds(1800)));
        jdbc.update("INSERT INTO kitchen_tickets (order_id, status, created_at, updated_at) "
                + "VALUES (?, 'QUEUED', ?, ?)", open, timestamp(FROM.plusSeconds(2400)),
                timestamp(FROM.plusSeconds(2400)));
        jdbc.update("INSERT INTO kitchen_tickets "
                + "(order_id, status, created_at, updated_at, ready_at, cancelled_at) "
                + "VALUES (?, 'CANCELLED', ?, ?, ?, ?)", submitted, timestamp(FROM.plusSeconds(3000)),
                timestamp(FROM.plusSeconds(3120)), timestamp(FROM.plusSeconds(3060)),
                timestamp(FROM.plusSeconds(3120)));
        seedInventory(actor);
        seedStaff();
    }

    private void seedInventory(long actor) {
        jdbc.update("INSERT INTO inventory_items (code, name, unit, reorder_threshold, active) "
                + "VALUES ('FLOUR-R', 'Flour', 'GRAM', 2.000, TRUE), "
                + "('BOX-R', 'Boxes', 'UNIT', 1.000, TRUE)");
        long flour = id("inventory_items", "code", "FLOUR-R");
        long boxes = id("inventory_items", "code", "BOX-R");
        movement(flour, "RECEIPT", "5.000", FROM, actor, "report-flour-receipt");
        movement(flour, "WASTE", "1.000", FROM.plusSeconds(1), actor, "report-flour-waste");
        movement(flour, "USAGE", "0.500", FROM.plusSeconds(2), actor, "report-flour-usage");
        movement(flour, "ADJUSTMENT_IN", "0.250", FROM.plusSeconds(3), actor, "report-flour-adjust-in");
        movement(flour, "ADJUSTMENT_OUT", "0.100", FROM.plusSeconds(4), actor, "report-flour-adjust-out");
        movement(boxes, "RECEIPT", "3.000", FROM.plusSeconds(5), actor, "report-box-receipt");
    }

    private void seedStaff() {
        jdbc.update("INSERT INTO employees "
                + "(employee_code, first_name, last_name, default_operational_role, active) "
                + "VALUES ('EMP-REPORT', 'Report', 'Employee', 'WAITER', TRUE)");
        long employee = id("employees", "employee_code", "EMP-REPORT");
        jdbc.update("INSERT INTO shifts "
                + "(employee_id, operational_role, start_at, end_at, status, completed_at) "
                + "VALUES (?, 'WAITER', ?, ?, 'COMPLETED', ?)", employee, timestamp(FROM.plusSeconds(3600)),
                timestamp(FROM.plusSeconds(9 * 3600)), timestamp(FROM.plusSeconds(10 * 3600)));
        jdbc.update("INSERT INTO shifts "
                + "(employee_id, operational_role, start_at, end_at, status, cancelled_at) "
                + "VALUES (?, 'WAITER', ?, ?, 'CANCELLED', ?)", employee, timestamp(FROM.plusSeconds(12 * 3600)),
                timestamp(FROM.plusSeconds(16 * 3600)), timestamp(FROM.plusSeconds(100)));
        jdbc.update("INSERT INTO shifts "
                + "(employee_id, operational_role, start_at, end_at, status) "
                + "VALUES (?, 'HOST', ?, ?, 'SCHEDULED')", employee,
                timestamp(FROM.plusSeconds(18 * 3600)), timestamp(FROM.plusSeconds(20 * 3600)));
    }

    private long order(long table, String number, String status, Instant at, String total) {
        jdbc.update("INSERT INTO orders "
                + "(order_number, restaurant_table_id, status, subtotal, total, created_at, completed_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, CASE WHEN ? = 'COMPLETED' THEN ? ELSE NULL END)",
                number, table, status, total, total, timestamp(at), status, timestamp(at));
        return id("orders", "order_number", number);
    }

    private void movement(long item, String type, String quantity, Instant at, long actor, String source) {
        jdbc.update("INSERT INTO stock_movements "
                + "(inventory_item_id, movement_type, quantity, occurred_at, actor_user_id, source_key) "
                + "VALUES (?, ?, ?, ?, ?, ?)", item, type, quantity, timestamp(at), actor, source);
    }

    private long id(String table, String column, String value) {
        return jdbc.queryForObject(
                "SELECT id FROM " + table + " WHERE " + column + " = ?", Long.class, value);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
            adminJwt() {
        return jwt().jwt(token -> token.subject("1").claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
