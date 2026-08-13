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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PaymentManagementIT {
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
    private Long tableId;
    private Long menuItemId;
    private Long modifierGroupId;
    private Long modifierOptionId;

    @BeforeEach
    void cleanAndSeed() {
        jdbcTemplate.update("DELETE FROM invoice_item_modifiers");
        jdbcTemplate.update("DELETE FROM invoice_items");
        jdbcTemplate.update("DELETE FROM invoices");
        jdbcTemplate.update("DELETE FROM payment_reconciliations");
        jdbcTemplate.update("DELETE FROM payments");
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
        jdbcTemplate.update("DELETE FROM restaurant_tables");
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM user_roles");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("DELETE FROM roles");

        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, display_name, enabled) VALUES (?, ?, ?, TRUE)",
                "payments-admin@example.com",
                "integration-test-password-hash",
                "Payments Admin");
        actorId = id("SELECT id FROM users WHERE email = 'payments-admin@example.com'");
        jdbcTemplate.update(
                "INSERT INTO restaurant_tables "
                        + "(table_number, display_name, capacity, section, status, active) "
                        + "VALUES ('PAY-1', 'Payment Table', 4, 'Main', 'AVAILABLE', TRUE)");
        tableId = id("SELECT id FROM restaurant_tables WHERE table_number = 'PAY-1'");
        jdbcTemplate.update(
                "INSERT INTO menu_categories (name, display_order, active) VALUES ('Payment Menu', 0, TRUE)");
        Long categoryId = id("SELECT id FROM menu_categories WHERE name = 'Payment Menu'");
        jdbcTemplate.update(
                "INSERT INTO menu_items "
                        + "(category_id, code, name, base_price, display_order, active, available_for_sale) "
                        + "VALUES (?, 'MEAL-PAY', 'Payment Meal', 25.00, 0, TRUE, TRUE)",
                categoryId);
        menuItemId = id("SELECT id FROM menu_items WHERE code = 'MEAL-PAY'");
        jdbcTemplate.update(
                "INSERT INTO modifier_groups "
                        + "(name, selection_type, minimum_selections, maximum_selections, display_order, active) "
                        + "VALUES ('Payment Extras', 'MULTIPLE', 0, 1, 0, TRUE)");
        modifierGroupId = id("SELECT id FROM modifier_groups WHERE name = 'Payment Extras'");
        jdbcTemplate.update(
                "INSERT INTO modifier_options "
                        + "(modifier_group_id, name, price_adjustment, display_order, active) "
                        + "VALUES (?, 'Payment Sauce', 2.00, 0, TRUE)",
                modifierGroupId);
        modifierOptionId = id("SELECT id FROM modifier_options WHERE name = 'Payment Sauce'");
    }

    @Test
    void confirmedSplitPaymentsAreExactIdempotentAndReconciledOnce() throws Exception {
        long orderId = order("COMPLETED", "40.00", "ORD-PAY-SPLIT");

        MvcResult cash = payment(orderId, "idem-split-cash-0001", "10.00", "CASH", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentNumber").value(org.hamcrest.Matchers.startsWith("PAY-")))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.amount").value("10.00"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andReturn();
        long cashId = number(cash, "$.id");

        payment(orderId, "idem-split-cash-0001", "10.00", "CASH", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cashId));
        payment(orderId, "idem-split-cash-0001", "11.00", "CASH", null)
                .andExpect(status().isConflict());

        MvcResult card = payment(orderId, "idem-split-card-0002", "30.00", "CARD", "TERM-REF-001")
                .andExpect(status().isCreated())
                .andReturn();
        long cardId = number(card, "$.id");

        mockMvc.perform(get("/api/v1/payments/orders/{id}/summary", orderId).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderTotal").value("40.00"))
                .andExpect(jsonPath("$.paidAmount").value("40.00"))
                .andExpect(jsonPath("$.outstandingAmount").value("0.00"))
                .andExpect(jsonPath("$.paymentState").value("PAID"))
                .andExpect(jsonPath("$.payments.length()").value(2));

        payment(orderId, "idem-overpay-0003", "1.00", "CASH", null)
                .andExpect(status().isConflict());
        payment(orderId, "idem-card-no-ref-0004", "1.00", "CARD", null)
                .andExpect(status().isBadRequest());

        reconcile(cardId, "BATCH-001").andExpect(status().isCreated());
        reconcile(cardId, "BATCH-CHANGED-BUT-IDEMPOTENT").andExpect(status().isOk());

        assertThat(count("SELECT COUNT(*) FROM payments WHERE order_id = ?", orderId)).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM payment_reconciliations WHERE payment_id = ?", cardId))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE action = 'PAYMENT_RECORDED'")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE action = 'PAYMENT_RECONCILED'"))
                .isEqualTo(1);
    }

    @Test
    void invalidOrderStatesAndDuplicateExternalReferencesFailSafely() throws Exception {
        for (String orderStatus : List.of("OPEN", "SUBMITTED", "CANCELLED")) {
            long orderId = order(orderStatus, "15.00", "ORD-STATE-" + orderStatus);
            payment(
                            orderId,
                            "idem-state-" + orderStatus.toLowerCase() + "-0001",
                            "5.00",
                            "CASH",
                            null)
                    .andExpect(status().isConflict());
        }

        long first = order("COMPLETED", "15.00", "ORD-REFERENCE-ONE");
        long second = order("COMPLETED", "15.00", "ORD-REFERENCE-TWO");
        payment(first, "idem-reference-first-0001", "5.00", "BANK_TRANSFER", "BANK-REF-001")
                .andExpect(status().isCreated());
        payment(second, "idem-reference-second-0002", "5.00", "BANK_TRANSFER", "BANK-REF-001")
                .andExpect(status().isConflict());
        payment(second, null, "5.00", "CASH", null).andExpect(status().isBadRequest());
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE action = 'PAYMENT_RECORDED'"))
                .isEqualTo(1);
    }

    @Test
    void fullyPaidOrderProducesOneImmutableInvoiceSnapshot() throws Exception {
        long orderId = order("COMPLETED", "27.00", "ORD-INVOICE-ONE");
        payment(orderId, "idem-invoice-payment-0001", "27.00", "CARD", "TERM-INVOICE-001")
                .andExpect(status().isCreated());

        MvcResult issued = issueInvoice(orderId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceNumber").value(org.hamcrest.Matchers.startsWith("INV-")))
                .andExpect(jsonPath("$.orderNumber").value("ORD-INVOICE-ONE"))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.subtotal").value("27.00"))
                .andExpect(jsonPath("$.total").value("27.00"))
                .andExpect(jsonPath("$.paidTotal").value("27.00"))
                .andExpect(jsonPath("$.items[0].itemCode").value("MEAL-PAY"))
                .andExpect(jsonPath("$.items[0].itemName").value("Payment Meal"))
                .andExpect(jsonPath("$.items[0].modifiers[0].groupName").value("Payment Extras"))
                .andExpect(jsonPath("$.items[0].modifiers[0].optionName").value("Payment Sauce"))
                .andReturn();
        long invoiceId = number(issued, "$.id");
        issueInvoice(orderId).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(invoiceId));

        jdbcTemplate.update(
                "UPDATE menu_items SET name = 'Renamed Meal', base_price = 99.00 WHERE id = ?",
                menuItemId);
        jdbcTemplate.update(
                "UPDATE modifier_options SET name = 'Renamed Sauce', price_adjustment = 9.00 WHERE id = ?",
                modifierOptionId);
        mockMvc.perform(get("/api/v1/invoices/{id}", invoiceId).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].itemName").value("Payment Meal"))
                .andExpect(jsonPath("$.items[0].basePrice").value("25.00"))
                .andExpect(jsonPath("$.items[0].modifiers[0].optionName").value("Payment Sauce"))
                .andExpect(jsonPath("$.items[0].modifiers[0].priceAdjustment").value("2.00"));

        assertThat(count("SELECT COUNT(*) FROM invoices WHERE order_id = ?", orderId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE action = 'INVOICE_ISSUED'"))
                .isEqualTo(1);
    }

    @Test
    void unpaidAndPartiallyPaidOrdersCannotBeInvoiced() throws Exception {
        long unpaid = order("COMPLETED", "20.00", "ORD-INVOICE-UNPAID");
        issueInvoice(unpaid).andExpect(status().isConflict());
        payment(unpaid, "idem-partial-invoice-0001", "5.00", "CASH", null)
                .andExpect(status().isCreated());
        issueInvoice(unpaid).andExpect(status().isConflict());

        for (String orderStatus : List.of("OPEN", "SUBMITTED", "CANCELLED")) {
            issueInvoice(order(orderStatus, "20.00", "ORD-INVOICE-" + orderStatus))
                    .andExpect(status().isConflict());
        }
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE action = 'INVOICE_ISSUED'"))
                .isZero();
    }

    @Test
    void simultaneousFinalPaymentsSerializeToOneSuccessAndOneConflict() throws Exception {
        long orderId = order("COMPLETED", "25.00", "ORD-CONCURRENT-PAYMENT");
        CyclicBarrier barrier = new CyclicBarrier(3);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> concurrentPayment(
                    barrier, orderId, "idem-concurrent-payment-a", "TERM-CONCURRENT-A"));
            Future<Integer> second = executor.submit(() -> concurrentPayment(
                    barrier, orderId, "idem-concurrent-payment-b", "TERM-CONCURRENT-B"));
            barrier.await(10, TimeUnit.SECONDS);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0.00) FROM payments WHERE order_id = ?",
                java.math.BigDecimal.class,
                orderId)).isEqualByComparingTo("25.00");
        assertThat(count("SELECT COUNT(*) FROM payments WHERE order_id = ?", orderId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE action = 'PAYMENT_RECORDED'"))
                .isEqualTo(1);
    }

    @Test
    void simultaneousIdenticalIdempotentPaymentsCreateOneRowAndOneAudit() throws Exception {
        long orderId = order("COMPLETED", "25.00", "ORD-CONCURRENT-IDEMPOTENT");
        CyclicBarrier barrier = new CyclicBarrier(3);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> concurrentPayment(
                    barrier, orderId, "idem-concurrent-same-key", "TERM-SAME-KEY"));
            Future<Integer> second = executor.submit(() -> concurrentPayment(
                    barrier, orderId, "idem-concurrent-same-key", "TERM-SAME-KEY"));
            barrier.await(10, TimeUnit.SECONDS);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 200);
        }
        assertThat(count("SELECT COUNT(*) FROM payments WHERE order_id = ?", orderId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE action = 'PAYMENT_RECORDED'"))
                .isEqualTo(1);
    }

    @Test
    void simultaneousInvoiceIssuanceReturnsTheSameSingleInvoice() throws Exception {
        long orderId = order("COMPLETED", "25.00", "ORD-CONCURRENT-INVOICE");
        payment(orderId, "idem-concurrent-invoice-pay", "25.00", "CASH", null)
                .andExpect(status().isCreated());
        CyclicBarrier barrier = new CyclicBarrier(3);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> concurrentInvoice(barrier, orderId));
            Future<Integer> second = executor.submit(() -> concurrentInvoice(barrier, orderId));
            barrier.await(10, TimeUnit.SECONDS);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 200);
        }
        assertThat(count("SELECT COUNT(*) FROM invoices WHERE order_id = ?", orderId)).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE action = 'INVOICE_ISSUED'"))
                .isEqualTo(1);
    }

    private long order(String status, String total, String orderNumber) {
        jdbcTemplate.update(
                "INSERT INTO orders "
                        + "(order_number, restaurant_table_id, status, subtotal, total, completed_at) "
                        + "VALUES (?, ?, ?, ?, ?, CASE WHEN ? = 'COMPLETED' THEN UTC_TIMESTAMP(6) ELSE NULL END)",
                orderNumber,
                tableId,
                status,
                total,
                total,
                status);
        long orderId = id("SELECT id FROM orders WHERE order_number = '" + orderNumber + "'");
        jdbcTemplate.update(
                "INSERT INTO order_items "
                        + "(order_id, menu_item_id, item_code_snapshot, item_name_snapshot, "
                        + "base_price_snapshot, quantity, unit_total_snapshot, line_total, display_order) "
                        + "VALUES (?, ?, 'MEAL-PAY', 'Payment Meal', 25.00, 1, ?, ?, 0)",
                orderId,
                menuItemId,
                total,
                total);
        Long orderItemId = id("SELECT id FROM order_items WHERE order_id = " + orderId);
        if ("27.00".equals(total)) {
            jdbcTemplate.update(
                    "INSERT INTO order_item_modifiers "
                            + "(order_item_id, modifier_group_id, modifier_option_id, group_name_snapshot, "
                            + "option_name_snapshot, price_adjustment_snapshot, display_order) "
                            + "VALUES (?, ?, ?, 'Payment Extras', 'Payment Sauce', 2.00, 0)",
                    orderItemId,
                    modifierGroupId,
                    modifierOptionId);
        }
        return orderId;
    }

    private org.springframework.test.web.servlet.ResultActions payment(
            long orderId,
            String key,
            String amount,
            String method,
            String externalReference) throws Exception {
        String reference = externalReference == null ? "null" : "\"" + externalReference + "\"";
        var request = post("/api/v1/payments/orders/{id}", orderId)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":" + amount + ",\"method\":\"" + method
                        + "\",\"externalReference\":" + reference + "}");
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    private org.springframework.test.web.servlet.ResultActions reconcile(long paymentId, String reference)
            throws Exception {
        return mockMvc.perform(post("/api/v1/payments/{id}/reconciliation", paymentId)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reconciliationReference\":\"" + reference + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions issueInvoice(long orderId) throws Exception {
        return mockMvc.perform(post("/api/v1/invoices/orders/{id}", orderId).with(adminJwt()));
    }

    private int concurrentPayment(CyclicBarrier barrier, long orderId, String key, String reference)
            throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        return payment(orderId, key, "25.00", "CARD", reference)
                .andReturn().getResponse().getStatus();
    }

    private int concurrentInvoice(CyclicBarrier barrier, long orderId) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        return issueInvoice(orderId).andReturn().getResponse().getStatus();
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
            adminJwt() {
        return jwt().jwt(token -> token.subject(actorId.toString()).claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private Long id(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    private int count(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, Integer.class, arguments);
    }

    private long number(MvcResult result, String path) throws java.io.UnsupportedEncodingException {
        Number value = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }
}
