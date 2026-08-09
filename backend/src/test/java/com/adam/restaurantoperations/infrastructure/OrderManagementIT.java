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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class OrderManagementIT {
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
    @Autowired private JdbcTemplate jdbcTemplate;
    private Long actorId;
    private Long availableTableId;
    private Long unavailableTableId;
    private Long seatedReservationId;
    private Long nonSeatedReservationId;
    private Long menuItemId;
    private Long sizeGroupId;
    private Long extrasGroupId;
    private Long regularOptionId;
    private Long largeOptionId;
    private Long cheeseOptionId;

    @BeforeEach
    void cleanAndSeed() {
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
                "orders-admin@example.com",
                "integration-test-password-hash",
                "Orders Admin");
        actorId = id("SELECT id FROM users WHERE email = 'orders-admin@example.com'");

        jdbcTemplate.update(
                "INSERT INTO restaurant_tables "
                        + "(table_number, display_name, capacity, section, status, active) "
                        + "VALUES ('T-1', 'Window One', 4, 'Window', 'AVAILABLE', TRUE), "
                        + "('T-2', 'Patio Two', 4, 'Patio', 'OUT_OF_SERVICE', TRUE)");
        availableTableId = id("SELECT id FROM restaurant_tables WHERE table_number = 'T-1'");
        unavailableTableId = id("SELECT id FROM restaurant_tables WHERE table_number = 'T-2'");

        jdbcTemplate.update(
                "INSERT INTO reservations "
                        + "(reservation_code, guest_name, guest_phone, party_size, start_at, duration_minutes, "
                        + "restaurant_table_id, status) VALUES "
                        + "('RES-SEATED', 'Seated Guest', '+10000000001', 2, UTC_TIMESTAMP(6), 90, ?, 'SEATED'), "
                        + "('RES-CONFIRMED', 'Waiting Guest', '+10000000002', 2, UTC_TIMESTAMP(6), 90, ?, 'CONFIRMED')",
                availableTableId,
                availableTableId);
        seatedReservationId = id("SELECT id FROM reservations WHERE reservation_code = 'RES-SEATED'");
        nonSeatedReservationId = id("SELECT id FROM reservations WHERE reservation_code = 'RES-CONFIRMED'");

        jdbcTemplate.update("INSERT INTO menu_categories (name, display_order, active) VALUES ('Mains', 0, TRUE)");
        Long categoryId = id("SELECT id FROM menu_categories WHERE name = 'Mains'");
        jdbcTemplate.update(
                "INSERT INTO menu_items "
                        + "(category_id, code, name, base_price, display_order, active, available_for_sale) "
                        + "VALUES (?, 'BURGER', 'Burger', 10.00, 0, TRUE, TRUE)",
                categoryId);
        menuItemId = id("SELECT id FROM menu_items WHERE code = 'BURGER'");

        jdbcTemplate.update(
                "INSERT INTO modifier_groups "
                        + "(name, selection_type, minimum_selections, maximum_selections, display_order, active) "
                        + "VALUES ('Size', 'SINGLE', 1, 1, 0, TRUE), "
                        + "('Extras', 'MULTIPLE', 0, 2, 1, TRUE)");
        sizeGroupId = id("SELECT id FROM modifier_groups WHERE name = 'Size'");
        extrasGroupId = id("SELECT id FROM modifier_groups WHERE name = 'Extras'");
        jdbcTemplate.update(
                "INSERT INTO modifier_options "
                        + "(modifier_group_id, name, price_adjustment, display_order, active) VALUES "
                        + "(?, 'Regular', 0.00, 0, TRUE), (?, 'Large', 2.00, 1, TRUE), "
                        + "(?, 'Cheese', 1.50, 0, TRUE), (?, 'Bacon', 2.00, 1, TRUE)",
                sizeGroupId,
                sizeGroupId,
                extrasGroupId,
                extrasGroupId);
        regularOptionId = id("SELECT id FROM modifier_options WHERE name = 'Regular'");
        largeOptionId = id("SELECT id FROM modifier_options WHERE name = 'Large'");
        cheeseOptionId = id("SELECT id FROM modifier_options WHERE name = 'Cheese'");
        jdbcTemplate.update(
                "INSERT INTO menu_item_modifier_groups "
                        + "(menu_item_id, modifier_group_id, display_order) VALUES (?, ?, 0), (?, ?, 1)",
                menuItemId,
                sizeGroupId,
                menuItemId,
                extrasGroupId);
    }

    @Test
    void creationEnforcesTableAndReservationRulesAndCreatesOpenHistory() throws Exception {
        createOrder(availableTableId, seatedReservationId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value(org.hamcrest.Matchers.startsWith("ORD-")))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.restaurantTable.id").value(availableTableId))
                .andExpect(jsonPath("$.reservation.id").value(seatedReservationId))
                .andExpect(jsonPath("$.subtotal").value("0.00"))
                .andExpect(jsonPath("$.history[0].fromStatus").isEmpty())
                .andExpect(jsonPath("$.history[0].toStatus").value("OPEN"));

        createOrder(unavailableTableId, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
        createOrder(availableTableId, nonSeatedReservationId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only a SEATED reservation may be linked to an order"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'ORDER_CREATED' AND actor_user_id = ?",
                Integer.class,
                actorId)).isEqualTo(1);
    }

    @Test
    void snapshotsTotalsEditingAndLifecycleRemainCommerciallyImmutable() throws Exception {
        MvcResult created = createOrder(availableTableId, null).andExpect(status().isCreated()).andReturn();
        long orderId = number(created, "$.id");
        long version = number(created, "$.version");

        MvcResult added = addBurger(orderId, version, 2, regularOptionId, cheeseOptionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].itemName").value("Burger"))
                .andExpect(jsonPath("$.items[0].basePrice").value("10.00"))
                .andExpect(jsonPath("$.items[0].unitTotal").value("11.50"))
                .andExpect(jsonPath("$.items[0].lineTotal").value("23.00"))
                .andExpect(jsonPath("$.subtotal").value("23.00"))
                .andExpect(jsonPath("$.total").value("23.00"))
                .andReturn();
        long firstItemId = number(added, "$.items[0].id");

        jdbcTemplate.update("UPDATE menu_items SET name = 'Signature Burger', base_price = 12.00 WHERE id = ?", menuItemId);
        jdbcTemplate.update("UPDATE modifier_options SET name = 'Large Plus', price_adjustment = 3.00 WHERE id = ?", largeOptionId);

        mockMvc.perform(get("/api/v1/orders/{id}", orderId).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].itemName").value("Burger"))
                .andExpect(jsonPath("$.items[0].basePrice").value("10.00"))
                .andExpect(jsonPath("$.items[0].modifiers[1].optionName").value("Cheese"));

        MvcResult secondAdded = addBurger(
                        orderId,
                        number(added, "$.version"),
                        1,
                        largeOptionId,
                        cheeseOptionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[1].itemName").value("Signature Burger"))
                .andExpect(jsonPath("$.items[1].basePrice").value("12.00"))
                .andExpect(jsonPath("$.items[1].unitTotal").value("16.50"))
                .andExpect(jsonPath("$.total").value("39.50"))
                .andReturn();
        long secondItemId = number(secondAdded, "$.items[1].id");

        MvcResult quantityUpdated = mockMvc.perform(put("/api/v1/orders/{id}/items/{itemId}", orderId, firstItemId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3,\"notes\":\"No onions\",\"version\":"
                                + number(secondAdded, "$.version") + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].itemName").value("Burger"))
                .andExpect(jsonPath("$.items[0].unitTotal").value("11.50"))
                .andExpect(jsonPath("$.items[0].lineTotal").value("34.50"))
                .andReturn();

        MvcResult repriced = mockMvc.perform(put("/api/v1/orders/{id}/items/{itemId}", orderId, firstItemId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(itemUpdateJson(number(quantityUpdated, "$.version"), largeOptionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].itemName").value("Signature Burger"))
                .andExpect(jsonPath("$.items[0].basePrice").value("12.00"))
                .andExpect(jsonPath("$.items[0].unitTotal").value("15.00"))
                .andExpect(jsonPath("$.items[0].modifiers[0].optionName").value("Large Plus"))
                .andReturn();

        MvcResult removed = mockMvc.perform(delete("/api/v1/orders/{id}/items/{itemId}", orderId, secondItemId)
                        .with(adminJwt())
                        .queryParam("version", Long.toString(number(repriced, "$.version"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemCount").value(1))
                .andExpect(jsonPath("$.total").value("15.00"))
                .andReturn();

        MvcResult submitted = transition(orderId, number(removed, "$.version"), "SUBMITTED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submittedAt").isNotEmpty())
                .andReturn();

        mockMvc.perform(put("/api/v1/orders/{id}/items/{itemId}", orderId, firstItemId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":2,\"version\":" + number(submitted, "$.version") + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Only OPEN orders may be modified"));

        transition(orderId, number(submitted, "$.version"), "COMPLETED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.history.length()").value(3));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE entity_type = 'ORDER' "
                        + "AND action IN ('ORDER_CREATED','ORDER_ITEM_ADDED','ORDER_ITEM_UPDATED',"
                        + "'ORDER_ITEM_REMOVED','ORDER_SUBMITTED','ORDER_COMPLETED')",
                Integer.class)).isEqualTo(8);
    }

    @Test
    void invalidModifierSelectionsAndUnavailableItemsFailSafely() throws Exception {
        MvcResult created = createOrder(availableTableId, null).andReturn();
        long orderId = number(created, "$.id");
        long version = number(created, "$.version");

        mockMvc.perform(post("/api/v1/orders/{id}/items", orderId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItemJson(version, 1, sizeGroupId, List.of(), extrasGroupId, List.of())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Modifier selections do not satisfy current rules"));

        mockMvc.perform(post("/api/v1/orders/{id}/items", orderId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addItemJson(
                                version,
                                1,
                                sizeGroupId,
                                List.of(regularOptionId, regularOptionId),
                                extrasGroupId,
                                List.of())))
                .andExpect(status().isBadRequest());

        jdbcTemplate.update("UPDATE menu_items SET available_for_sale = FALSE WHERE id = ?", menuItemId);
        addBurger(orderId, version, 1, regularOptionId, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Menu item is not currently available for sale"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_items", Integer.class)).isZero();
    }

    @Test
    void itemMutationRacingSubmissionHasOneWinnerAndConsistentFinalState() throws Exception {
        MvcResult created = createOrder(availableTableId, null).andReturn();
        long orderId = number(created, "$.id");
        MvcResult initial = addBurger(orderId, number(created, "$.version"), 1, regularOptionId, null)
                .andReturn();
        long version = number(initial, "$.version");

        CyclicBarrier start = new CyclicBarrier(3);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> submit = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return transition(orderId, version, "SUBMITTED").andReturn().getResponse().getStatus();
            });
            Future<Integer> add = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return addBurger(orderId, version, 1, regularOptionId, null)
                        .andReturn().getResponse().getStatus();
            });
            start.await(10, TimeUnit.SECONDS);
            assertThat(List.of(submit.get(20, TimeUnit.SECONDS), add.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }

        String status = jdbcTemplate.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
        int itemCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_items WHERE order_id = ?", Integer.class, orderId);
        assertThat(status.equals("SUBMITTED") ? itemCount == 1 : itemCount == 2).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT total = (SELECT COALESCE(SUM(line_total), 0) FROM order_items WHERE order_id = ?) "
                        + "FROM orders WHERE id = ?",
                Boolean.class,
                orderId,
                orderId)).isTrue();
    }

    private org.springframework.test.web.servlet.ResultActions createOrder(Long tableId, Long reservationId)
            throws Exception {
        String reservation = reservationId == null ? "null" : reservationId.toString();
        return mockMvc.perform(post("/api/v1/orders")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"restaurantTableId\":" + tableId + ",\"reservationId\":" + reservation
                        + ",\"notes\":\"Dinner service\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions addBurger(
            long orderId,
            long version,
            int quantity,
            Long sizeOption,
            Long extraOption) throws Exception {
        List<Long> size = sizeOption == null ? List.of() : List.of(sizeOption);
        List<Long> extras = extraOption == null ? List.of() : List.of(extraOption);
        return mockMvc.perform(post("/api/v1/orders/{id}/items", orderId)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(addItemJson(version, quantity, sizeGroupId, size, extrasGroupId, extras)));
    }

    private String addItemJson(
            long version,
            int quantity,
            long firstGroup,
            List<Long> firstOptions,
            long secondGroup,
            List<Long> secondOptions) {
        return """
                {"menuItemId":%d,"quantity":%d,"notes":"Prepared fresh","version":%d,
                 "modifierSelections":[
                   {"modifierGroupId":%d,"optionIds":%s},
                   {"modifierGroupId":%d,"optionIds":%s}]}
                """.formatted(
                menuItemId,
                quantity,
                version,
                firstGroup,
                firstOptions,
                secondGroup,
                secondOptions);
    }

    private String itemUpdateJson(long version, long sizeOptionId) {
        return """
                {"quantity":1,"notes":"Repriced selection","version":%d,
                 "modifierSelections":[
                   {"modifierGroupId":%d,"optionIds":[%d]},
                   {"modifierGroupId":%d,"optionIds":[]}]}
                """.formatted(version, sizeGroupId, sizeOptionId, extrasGroupId);
    }

    private org.springframework.test.web.servlet.ResultActions transition(
            long orderId,
            long version,
            String status) throws Exception {
        return mockMvc.perform(patch("/api/v1/orders/{id}/status", orderId)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + status + "\",\"version\":" + version + "}"));
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
            adminJwt() {
        return jwt().jwt(token -> token.subject(actorId.toString()).claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private Long id(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    private long number(MvcResult result, String path) throws java.io.UnsupportedEncodingException {
        Number value = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }
}
