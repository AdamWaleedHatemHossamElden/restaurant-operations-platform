package com.adam.restaurantoperations.infrastructure;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class KitchenManagementIT {
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

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    private Long actorId;
    private Long tableId;
    private Long menuItemId;
    private Long modifierGroupId;
    private Long modifierOptionId;

    @BeforeEach
    void cleanAndSeed() {
        jdbcTemplate.update("DELETE FROM kitchen_ticket_items");
        jdbcTemplate.update("DELETE FROM kitchen_tickets");
        jdbcTemplate.update("DELETE FROM order_status_history");
        jdbcTemplate.update("DELETE FROM order_item_modifiers");
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM menu_item_modifier_groups");
        jdbcTemplate.update("DELETE FROM modifier_options");
        jdbcTemplate.update("DELETE FROM modifier_groups");
        jdbcTemplate.update("DELETE FROM menu_items");
        jdbcTemplate.update("DELETE FROM menu_categories");
        jdbcTemplate.update("DELETE FROM reservations");
        jdbcTemplate.update("DELETE FROM restaurant_tables");
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM user_roles");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("DELETE FROM roles");

        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, display_name, enabled) VALUES (?, ?, ?, TRUE)",
                "kitchen-admin@example.com",
                "integration-test-password-hash",
                "Kitchen Admin");
        actorId = id("SELECT id FROM users WHERE email = 'kitchen-admin@example.com'");
        jdbcTemplate.update(
                "INSERT INTO restaurant_tables "
                        + "(table_number, display_name, capacity, section, status, active) "
                        + "VALUES ('K-1', 'Kitchen One', 4, 'Main', 'AVAILABLE', TRUE)");
        tableId = id("SELECT id FROM restaurant_tables WHERE table_number = 'K-1'");
        jdbcTemplate.update("INSERT INTO menu_categories (name, display_order, active) VALUES ('Kitchen', 0, TRUE)");
        Long categoryId = id("SELECT id FROM menu_categories WHERE name = 'Kitchen'");
        jdbcTemplate.update(
                "INSERT INTO menu_items "
                        + "(category_id, code, name, base_price, display_order, active, available_for_sale) "
                        + "VALUES (?, 'SOUP', 'Soup snapshot', 8.00, 0, TRUE, TRUE)",
                categoryId);
        menuItemId = id("SELECT id FROM menu_items WHERE code = 'SOUP'");
        jdbcTemplate.update(
                "INSERT INTO modifier_groups "
                        + "(name, selection_type, minimum_selections, maximum_selections, display_order, active) "
                        + "VALUES ('Garnish snapshot', 'MULTIPLE', 0, 1, 0, TRUE)");
        modifierGroupId = id("SELECT id FROM modifier_groups WHERE name = 'Garnish snapshot'");
        jdbcTemplate.update(
                "INSERT INTO modifier_options "
                        + "(modifier_group_id, name, price_adjustment, display_order, active) "
                        + "VALUES (?, 'Herbs snapshot', 0.00, 0, TRUE)",
                modifierGroupId);
        modifierOptionId = id("SELECT id FROM modifier_options WHERE name = 'Herbs snapshot'");
        jdbcTemplate.update(
                "INSERT INTO menu_item_modifier_groups "
                        + "(menu_item_id, modifier_group_id, display_order) VALUES (?, ?, 0)",
                menuItemId,
                modifierGroupId);
    }

    @Test
    void submissionCreatesSnapshotTicketAndReadyIsRequiredForCompletion() throws Exception {
        MvcResult created = createOrder().andExpect(status().isCreated()).andReturn();
        long orderId = number(created, "$.id");
        assertThat(count("SELECT COUNT(*) FROM kitchen_tickets WHERE order_id = ?", orderId)).isZero();

        MvcResult first = addItem(orderId, number(created, "$.version"), 2, "First bowl").andReturn();
        MvcResult second = addItem(orderId, number(first, "$.version"), 1, "Second bowl").andReturn();
        MvcResult submitted = transitionOrder(orderId, number(second, "$.version"), "SUBMITTED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn();

        long ticketId = id("SELECT id FROM kitchen_tickets WHERE order_id = " + orderId);
        assertThat(count("SELECT COUNT(*) FROM kitchen_tickets WHERE order_id = ?", orderId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM kitchen_ticket_items WHERE kitchen_ticket_id = ?", ticketId))
                .isEqualTo(2);
        assertThat(count(
                "SELECT COUNT(*) FROM kitchen_ticket_items WHERE kitchen_ticket_id = ? AND status = 'QUEUED'",
                ticketId)).isEqualTo(2);
        mockMvc.perform(get("/api/v1/kitchen/tickets/{id}", ticketId).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.items[0].itemName").value("Soup snapshot"))
                .andExpect(jsonPath("$.items[0].modifiers[0].groupName").value("Garnish snapshot"))
                .andExpect(jsonPath("$.items[0].modifiers[0].optionName").value("Herbs snapshot"))
                .andExpect(jsonPath("$.items[0].notes").value("First bowl"));

        transitionOrder(orderId, number(submitted, "$.version"), "COMPLETED")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Kitchen ticket must be READY before completing the order"));

        List<Long> itemIds = jdbcTemplate.queryForList(
                "SELECT id FROM kitchen_ticket_items WHERE kitchen_ticket_id = ? ORDER BY id",
                Long.class,
                ticketId);
        long version = ticketVersion(ticketId);
        MvcResult preparing = transitionItem(ticketId, itemIds.get(0), version, "PREPARING")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andReturn();
        transitionItem(ticketId, itemIds.get(0), number(preparing, "$.version"), "READY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"));
        MvcResult secondPreparing = transitionItem(ticketId, itemIds.get(1), ticketVersion(ticketId), "PREPARING")
                .andExpect(status().isOk())
                .andReturn();
        transitionItem(ticketId, itemIds.get(1), number(secondPreparing, "$.version"), "READY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.readyAt").isNotEmpty());

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId))
                .isEqualTo("SUBMITTED");
        jdbcTemplate.update("UPDATE menu_items SET name = 'Renamed current soup' WHERE id = ?", menuItemId);
        jdbcTemplate.update(
                "UPDATE modifier_options SET name = 'Renamed current herbs' WHERE id = ?",
                modifierOptionId);
        mockMvc.perform(get("/api/v1/kitchen/tickets/{id}", ticketId).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].itemName").value("Soup snapshot"))
                .andExpect(jsonPath("$.items[0].modifiers[0].optionName").value("Herbs snapshot"));

        transitionOrder(orderId, number(submitted, "$.version"), "COMPLETED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(count(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'KITCHEN_TICKET_CREATED' "
                        + "OR action = 'KITCHEN_TICKET_READY'"))
                .isEqualTo(2);
    }

    @Test
    void cancellationIntegrationPreservesHistoryAndStopsPreparation() throws Exception {
        MvcResult open = createOrder().andReturn();
        transitionOrder(number(open, "$.id"), number(open, "$.version"), "CANCELLED")
                .andExpect(status().isOk());
        assertThat(count("SELECT COUNT(*) FROM kitchen_tickets")).isZero();

        MvcResult created = createOrder().andReturn();
        long orderId = number(created, "$.id");
        MvcResult added = addItem(orderId, number(created, "$.version"), 1, "Keep history").andReturn();
        MvcResult submitted = transitionOrder(orderId, number(added, "$.version"), "SUBMITTED").andReturn();
        long ticketId = id("SELECT id FROM kitchen_tickets WHERE order_id = " + orderId);
        long itemId = id("SELECT id FROM kitchen_ticket_items WHERE kitchen_ticket_id = " + ticketId);
        transitionItem(ticketId, itemId, ticketVersion(ticketId), "PREPARING").andExpect(status().isOk());

        transitionOrder(orderId, number(submitted, "$.version"), "CANCELLED")
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM kitchen_tickets WHERE id = ?", String.class, ticketId))
                .isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM kitchen_ticket_items WHERE id = ?", String.class, itemId))
                .isEqualTo("PREPARING");
        transitionItem(ticketId, itemId, ticketVersion(ticketId), "READY")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Kitchen work is no longer active for this order"));
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE action = 'KITCHEN_TICKET_CANCELLED'"))
                .isEqualTo(1);
    }

    @Test
    void failedOrDuplicateSubmissionDoesNotCreateDuplicateKitchenState() throws Exception {
        MvcResult empty = createOrder().andReturn();
        transitionOrder(number(empty, "$.id"), number(empty, "$.version"), "SUBMITTED")
                .andExpect(status().isConflict());
        assertThat(count("SELECT COUNT(*) FROM kitchen_tickets")).isZero();

        MvcResult created = createOrder().andReturn();
        long orderId = number(created, "$.id");
        MvcResult added = addItem(orderId, number(created, "$.version"), 1, null).andReturn();
        MvcResult submitted = transitionOrder(orderId, number(added, "$.version"), "SUBMITTED").andReturn();
        transitionOrder(orderId, number(submitted, "$.version"), "SUBMITTED")
                .andExpect(status().isConflict());
        assertThat(count("SELECT COUNT(*) FROM kitchen_tickets WHERE order_id = ?", orderId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM kitchen_ticket_items")).isEqualTo(1);
    }

    @Test
    void concurrentSameItemProgressionHasOneWinnerAndConsistentAggregate() throws Exception {
        MvcResult created = createOrder().andReturn();
        long orderId = number(created, "$.id");
        MvcResult added = addItem(orderId, number(created, "$.version"), 1, null).andReturn();
        transitionOrder(orderId, number(added, "$.version"), "SUBMITTED").andReturn();
        long ticketId = id("SELECT id FROM kitchen_tickets WHERE order_id = " + orderId);
        long itemId = id("SELECT id FROM kitchen_ticket_items WHERE kitchen_ticket_id = " + ticketId);
        long version = ticketVersion(ticketId);

        CyclicBarrier start = new CyclicBarrier(3);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return transitionItem(ticketId, itemId, version, "PREPARING")
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> second = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return transitionItem(ticketId, itemId, version, "PREPARING")
                        .andReturn().getResponse().getStatus();
            });
            start.await(10, TimeUnit.SECONDS);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM kitchen_ticket_items WHERE id = ?", String.class, itemId))
                .isEqualTo("PREPARING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM kitchen_tickets WHERE id = ?", String.class, ticketId))
                .isEqualTo("PREPARING");
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE action = 'KITCHEN_ITEM_PREPARING'"))
                .isEqualTo(1);
    }

    private org.springframework.test.web.servlet.ResultActions createOrder() throws Exception {
        return mockMvc.perform(post("/api/v1/orders")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"restaurantTableId\":" + tableId + ",\"notes\":\"Kitchen test\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions addItem(
            long orderId,
            long version,
            int quantity,
            String notes) throws Exception {
        String noteValue = notes == null ? "null" : "\"" + notes + "\"";
        return mockMvc.perform(post("/api/v1/orders/{id}/items", orderId)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"menuItemId\":" + menuItemId + ",\"quantity\":" + quantity
                        + ",\"notes\":" + noteValue + ",\"modifierSelections\":["
                        + "{\"modifierGroupId\":" + modifierGroupId + ",\"optionIds\":["
                        + modifierOptionId + "]}],\"version\":" + version + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions transitionOrder(
            long orderId,
            long version,
            String status) throws Exception {
        return mockMvc.perform(patch("/api/v1/orders/{id}/status", orderId)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\",\"version\":" + version + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions transitionItem(
            long ticketId,
            long itemId,
            long version,
            String status) throws Exception {
        return mockMvc.perform(patch(
                        "/api/v1/kitchen/tickets/{ticketId}/items/{itemId}/status",
                        ticketId,
                        itemId)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\",\"version\":" + version + "}"));
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
            adminJwt() {
        return jwt().jwt(token -> token.subject(actorId.toString()).claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private long ticketVersion(long ticketId) {
        return jdbcTemplate.queryForObject(
                "SELECT version FROM kitchen_tickets WHERE id = ?", Long.class, ticketId);
    }

    private int count(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, Integer.class, arguments);
    }

    private Long id(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    private long number(MvcResult result, String path) throws java.io.UnsupportedEncodingException {
        Number value = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }
}
