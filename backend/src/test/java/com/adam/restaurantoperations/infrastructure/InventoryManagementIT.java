package com.adam.restaurantoperations.infrastructure;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.adam.restaurantoperations.inventory.InventoryDtos.IngredientInput;
import com.adam.restaurantoperations.inventory.InventoryDtos.InventoryItemRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.InventoryItemResponse;
import com.adam.restaurantoperations.inventory.InventoryDtos.ManualMovementRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.ModifierIngredientsRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.PurchaseOrderLineRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.PurchaseOrderRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.PurchaseOrderResponse;
import com.adam.restaurantoperations.inventory.InventoryDtos.PurchaseOrderStatusRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.PurchaseReceiptRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.RecipeIngredientsRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.RecipeResponse;
import com.adam.restaurantoperations.inventory.InventoryDtos.RecipeStateRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.SupplierItemRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.SupplierRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.SupplierResponse;
import com.adam.restaurantoperations.inventory.InventoryService;
import com.adam.restaurantoperations.inventory.InventoryUnit;
import com.adam.restaurantoperations.inventory.PurchaseOrderService;
import com.adam.restaurantoperations.inventory.PurchaseOrderStatus;
import com.adam.restaurantoperations.inventory.RecipeService;
import com.adam.restaurantoperations.inventory.StockMovementType;
import com.adam.restaurantoperations.inventory.SupplierService;
import com.adam.restaurantoperations.kitchen.KitchenDtos.KitchenItemStatusRequest;
import com.adam.restaurantoperations.kitchen.KitchenItemStatus;
import com.adam.restaurantoperations.kitchen.KitchenService;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class InventoryManagementIT {
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

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;
    @Autowired private InventoryService inventoryService;
    @Autowired private RecipeService recipeService;
    @Autowired private SupplierService supplierService;
    @Autowired private PurchaseOrderService purchaseOrderService;
    @Autowired private KitchenService kitchenService;

    private Long actorId;

    @BeforeEach
    void cleanAndSeedActor() {
        jdbcTemplate.update("DELETE FROM stock_movements");
        jdbcTemplate.update("DELETE FROM purchase_order_items");
        jdbcTemplate.update("DELETE FROM purchase_orders");
        jdbcTemplate.update("DELETE FROM supplier_inventory_items");
        jdbcTemplate.update("DELETE FROM suppliers");
        jdbcTemplate.update("DELETE FROM modifier_option_ingredients");
        jdbcTemplate.update("DELETE FROM recipe_ingredients");
        jdbcTemplate.update("DELETE FROM recipes");
        jdbcTemplate.update("DELETE FROM inventory_items");
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
                "inventory-admin@example.com",
                "integration-test-password-hash",
                "Inventory Admin");
        actorId = id("SELECT id FROM users WHERE email = 'inventory-admin@example.com'");
    }

    @Test
    void ledgerIsImmutableAuthoritativeAndAllowsVisibleNegativeStock() {
        InventoryItemResponse flour = item(" flour ", " Bread   Flour ", InventoryUnit.GRAM, "2.000");
        inventoryService.recordManualMovement(
                movement(flour.id(), StockMovementType.ADJUSTMENT_IN, "5.000", "Opening count"),
                actorId,
                "127.0.0.1");
        inventoryService.recordManualMovement(
                movement(flour.id(), StockMovementType.WASTE, "8.000", "Spoilage"),
                actorId,
                "127.0.0.1");

        InventoryItemResponse current = inventoryService.get(flour.id());
        assertThat(current.code()).isEqualTo("FLOUR");
        assertThat(current.name()).isEqualTo("Bread Flour");
        assertThat(current.onHand()).isEqualByComparingTo("-3.000");
        assertThat(current.lowStock()).isTrue();
        assertThatThrownBy(() -> inventoryService.update(
                        flour.id(),
                        new InventoryItemRequest(
                                flour.code(),
                                flour.name(),
                                InventoryUnit.UNIT,
                                flour.reorderThreshold(),
                                flour.version()),
                        actorId,
                        "127.0.0.1"))
                .hasMessageContaining("canonical unit cannot be changed");
        assertThat(inventoryService.movements(flour.id()))
                .extracting(movement -> movement.signedQuantity().toPlainString())
                .containsExactly("-8.000", "5.000");
        assertThat(inventoryService.list(null, true, null, "bread", "onHand",
                        org.springframework.data.domain.Sort.Direction.ASC))
                .extracting(InventoryItemResponse::id)
                .containsExactly(flour.id());
        assertThatThrownBy(() -> inventoryService.recordManualMovement(
                        movement(flour.id(), StockMovementType.RECEIPT, "1.000", null),
                        actorId,
                        "127.0.0.1"))
                .hasMessageContaining("Manual stock movements");
        InventoryItemResponse inactive = inventoryService.setActivation(
                flour.id(), false, current.version(), actorId, "127.0.0.1");
        assertThat(inactive.active()).isFalse();
        assertThat(count("SELECT COUNT(*) FROM stock_movements WHERE inventory_item_id = ?", flour.id()))
                .isEqualTo(2);
    }

    @Test
    void recipeAndSupplierConfigurationRejectsDuplicatesInactiveItemsAndStaleVersions() {
        InventoryItemResponse beef = item("BEEF", "Beef", InventoryUnit.GRAM, "100.000");
        assertThatThrownBy(() -> item(" beef ", "Other beef", InventoryUnit.GRAM, "0"))
                .hasMessageContaining("unique");
        KitchenFixture fixture = kitchenFixture(1);
        RecipeResponse recipe = recipeService.setRecipeState(
                fixture.menuItemId(), new RecipeStateRequest(true, null), actorId, "127.0.0.1");
        assertThatThrownBy(() -> recipeService.replaceIngredients(
                        fixture.menuItemId(),
                        new RecipeIngredientsRequest(
                                recipe.version(),
                                List.of(
                                        new IngredientInput(beef.id(), decimal("100"), 0),
                                        new IngredientInput(beef.id(), decimal("25"), 1))),
                        actorId,
                        "127.0.0.1"))
                .hasMessageContaining("only once");

        RecipeResponse configured = recipeService.replaceIngredients(
                fixture.menuItemId(),
                new RecipeIngredientsRequest(
                        recipe.version(), List.of(new IngredientInput(beef.id(), decimal("150"), 0))),
                actorId,
                "127.0.0.1");
        assertThatThrownBy(() -> recipeService.replaceIngredients(
                        fixture.menuItemId(),
                        new RecipeIngredientsRequest(
                                recipe.version(),
                                List.of(new IngredientInput(beef.id(), decimal("175"), 0))),
                        actorId,
                        "127.0.0.1"))
                .hasMessageContaining("changed by another request");
        inventoryService.setActivation(beef.id(), false, beef.version(), actorId, "127.0.0.1");
        assertThatThrownBy(() -> recipeService.replaceIngredients(
                        fixture.menuItemId(),
                        new RecipeIngredientsRequest(
                                configured.version(),
                                List.of(new IngredientInput(beef.id(), decimal("175"), 0))),
                        actorId,
                        "127.0.0.1"))
                .hasMessageContaining("Inactive inventory items");

        SupplierResponse supplier = supplierService.create(
                new SupplierRequest(" sup-1 ", "Primary Foods", null, null, null, null, null),
                actorId,
                "127.0.0.1");
        assertThat(supplier.code()).isEqualTo("SUP-1");
        assertThatThrownBy(() -> supplierService.create(
                        new SupplierRequest("SUP-1", "Duplicate", null, null, null, null, null),
                        actorId,
                        "127.0.0.1"))
                .hasMessageContaining("unique");
        SupplierResponse updated = supplierService.update(
                supplier.id(),
                new SupplierRequest(
                        supplier.code(), "Primary Foods Updated", null, null, null, null, supplier.version()),
                actorId,
                "127.0.0.1");
        assertThat(updated.name()).isEqualTo("Primary Foods Updated");
        assertThatThrownBy(() -> supplierService.update(
                        supplier.id(),
                        new SupplierRequest(
                                supplier.code(), "Stale", null, null, null, null, supplier.version()),
                        actorId,
                        "127.0.0.1"))
                .hasMessageContaining("changed by another request");
    }

    @Test
    void concurrentKitchenStartConsumesAggregatedRecipeAndModifierUsageExactlyOnce() throws Exception {
        InventoryItemResponse beef = item("BEEF", "Beef", InventoryUnit.GRAM, "1000.000");
        InventoryItemResponse cheese = item("CHEESE", "Cheese", InventoryUnit.GRAM, "100.000");
        KitchenFixture fixture = kitchenFixture(2);

        RecipeResponse recipe = recipeService.setRecipeState(
                fixture.menuItemId(), new RecipeStateRequest(true, null), actorId, "127.0.0.1");
        recipeService.replaceIngredients(
                fixture.menuItemId(),
                new RecipeIngredientsRequest(
                        recipe.version(), List.of(new IngredientInput(beef.id(), decimal("150"), 0))),
                actorId,
                "127.0.0.1");
        recipeService.replaceModifierIngredients(
                fixture.modifierOptionId(),
                new ModifierIngredientsRequest(
                        0L,
                        List.of(
                                new IngredientInput(beef.id(), decimal("10"), 0),
                                new IngredientInput(cheese.id(), decimal("30"), 1))),
                actorId,
                "127.0.0.1");

        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> attemptKitchenStart(fixture, barrier));
            Future<Boolean> second = executor.submit(() -> attemptKitchenStart(fixture, barrier));
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }

        assertThat(quantity(
                        "SELECT quantity FROM stock_movements WHERE inventory_item_id = ? AND movement_type = 'USAGE'",
                        beef.id()))
                .isEqualByComparingTo("320.000");
        assertThat(quantity(
                        "SELECT quantity FROM stock_movements WHERE inventory_item_id = ? AND movement_type = 'USAGE'",
                        cheese.id()))
                .isEqualByComparingTo("60.000");
        assertThat(count("SELECT COUNT(*) FROM stock_movements WHERE reference_type = 'KITCHEN_ITEM'"))
                .isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE action = 'STOCK_USAGE_RECORDED'"))
                .isEqualTo(1);

        long ticketVersion = number("SELECT version FROM kitchen_tickets WHERE id = ?", fixture.ticketId());
        kitchenService.transitionItem(
                fixture.ticketId(),
                fixture.kitchenItemId(),
                new KitchenItemStatusRequest(KitchenItemStatus.READY, ticketVersion),
                actorId,
                "127.0.0.1");
        assertThat(count("SELECT COUNT(*) FROM stock_movements WHERE reference_type = 'KITCHEN_ITEM'"))
                .isEqualTo(2);

        RecipeResponse latest = recipeService.getByMenuItem(fixture.menuItemId());
        recipeService.replaceIngredients(
                fixture.menuItemId(),
                new RecipeIngredientsRequest(
                        latest.version(), List.of(new IngredientInput(beef.id(), decimal("999"), 0))),
                actorId,
                "127.0.0.1");
        assertThat(quantity(
                        "SELECT quantity FROM stock_movements WHERE inventory_item_id = ? AND movement_type = 'USAGE'",
                        beef.id()))
                .isEqualByComparingTo("320.000");
    }

    @Test
    void purchaseOrdersSnapshotCostSupportPartialReceiptsAndPreserveCancelledReceipts() {
        InventoryItemResponse tomatoes = item("TOMATO", "Tomatoes", InventoryUnit.GRAM, "500.000");
        SupplierResponse supplier = supplier(tomatoes.id(), "2.5000");
        PurchaseOrderResponse order = draftWithLine(supplier.id(), tomatoes.id(), "10.000");

        supplierService.upsertItem(
                supplier.id(),
                new SupplierItemRequest(tomatoes.id(), "NEW-COST", decimal("9.9900"), true, 0L),
                actorId,
                "127.0.0.1");
        order = purchaseOrderService.transition(
                order.id(),
                new PurchaseOrderStatusRequest(PurchaseOrderStatus.ORDERED, order.version()),
                actorId,
                "127.0.0.1");
        assertThat(order.items().getFirst().unitCost()).isEqualByComparingTo("2.5000");

        order = purchaseOrderService.receive(
                order.id(),
                new PurchaseReceiptRequest(order.items().getFirst().id(), decimal("4"), order.version()),
                actorId,
                "127.0.0.1");
        assertThat(order.status()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        assertThat(order.items().getFirst().remainingQuantity()).isEqualByComparingTo("6.000");
        PurchaseOrderResponse cancelled = purchaseOrderService.transition(
                order.id(),
                new PurchaseOrderStatusRequest(PurchaseOrderStatus.CANCELLED, order.version()),
                actorId,
                "127.0.0.1");
        assertThat(cancelled.status()).isEqualTo(PurchaseOrderStatus.CANCELLED);
        assertThat(inventoryService.get(tomatoes.id()).onHand()).isEqualByComparingTo("4.000");
        assertThat(quantity(
                        "SELECT total_cost FROM stock_movements WHERE movement_type = 'RECEIPT' "
                                + "AND inventory_item_id = ?",
                        tomatoes.id()))
                .isEqualByComparingTo("10.0000");
        assertThatThrownBy(() -> purchaseOrderService.receive(
                        cancelled.id(),
                        new PurchaseReceiptRequest(
                                cancelled.items().getFirst().id(), decimal("1"), cancelled.version()),
                        actorId,
                        "127.0.0.1"))
                .hasMessageContaining("cannot receive stock");
    }

    @Test
    void purchaseOrderWorkflowIsExactVersionedFrozenAndTerminal() {
        InventoryItemResponse rice = item("RICE", "Rice", InventoryUnit.GRAM, "100.000");
        SupplierResponse supplier = supplier(rice.id(), "0.0050");
        PurchaseOrderResponse empty = purchaseOrderService.create(
                new PurchaseOrderRequest(supplier.id(), null, null), actorId, "127.0.0.1");
        assertThat(empty.purchaseOrderNumber()).startsWith("PO-");
        assertThatThrownBy(() -> purchaseOrderService.transition(
                        empty.id(),
                        new PurchaseOrderStatusRequest(PurchaseOrderStatus.ORDERED, empty.version()),
                        actorId,
                        "127.0.0.1"))
                .hasMessageContaining("at least one item");

        PurchaseOrderResponse draft = purchaseOrderService.addLine(
                empty.id(),
                new PurchaseOrderLineRequest(rice.id(), decimal("2"), empty.version()),
                actorId,
                "127.0.0.1");
        assertThat(draft.total()).isEqualByComparingTo("0.0100");
        draft = purchaseOrderService.updateLine(
                draft.id(),
                draft.items().getFirst().id(),
                new com.adam.restaurantoperations.inventory.InventoryDtos.PurchaseOrderLineUpdateRequest(
                        decimal("3"), draft.version()),
                actorId,
                "127.0.0.1");
        assertThat(draft.total()).isEqualByComparingTo("0.0150");
        PurchaseOrderResponse staleTarget = draft;
        assertThatThrownBy(() -> purchaseOrderService.update(
                        staleTarget.id(),
                        new PurchaseOrderRequest(supplier.id(), "Stale", staleTarget.version() - 1),
                        actorId,
                        "127.0.0.1"))
                .hasMessageContaining("changed by another request");

        PurchaseOrderResponse ordered = purchaseOrderService.transition(
                draft.id(),
                new PurchaseOrderStatusRequest(PurchaseOrderStatus.ORDERED, draft.version()),
                actorId,
                "127.0.0.1");
        assertThatThrownBy(() -> purchaseOrderService.updateLine(
                        ordered.id(),
                        ordered.items().getFirst().id(),
                        new com.adam.restaurantoperations.inventory.InventoryDtos.PurchaseOrderLineUpdateRequest(
                                decimal("4"), ordered.version()),
                        actorId,
                        "127.0.0.1"))
                .hasMessageContaining("Only DRAFT");
        assertThatThrownBy(() -> purchaseOrderService.receive(
                        ordered.id(),
                        new PurchaseReceiptRequest(
                                ordered.items().getFirst().id(), decimal("4"), ordered.version()),
                        actorId,
                        "127.0.0.1"))
                .hasMessageContaining("exceeds");
        assertThat(count("SELECT COUNT(*) FROM stock_movements")).isZero();

        PurchaseOrderResponse partial = purchaseOrderService.receive(
                ordered.id(),
                new PurchaseReceiptRequest(
                        ordered.items().getFirst().id(), decimal("1"), ordered.version()),
                actorId,
                "127.0.0.1");
        PurchaseOrderResponse received = purchaseOrderService.receive(
                partial.id(),
                new PurchaseReceiptRequest(
                        partial.items().getFirst().id(), decimal("2"), partial.version()),
                actorId,
                "127.0.0.1");
        assertThat(received.status()).isEqualTo(PurchaseOrderStatus.RECEIVED);
        assertThat(received.items().getFirst().receivedQuantity()).isEqualByComparingTo("3.000");
        assertThat(quantity("SELECT SUM(quantity) FROM stock_movements WHERE movement_type = 'RECEIPT'"))
                .isEqualByComparingTo("3.000");
        assertThat(count("SELECT COUNT(*) FROM stock_movements WHERE movement_type = 'RECEIPT'"))
                .isEqualTo(2);
        assertThatThrownBy(() -> purchaseOrderService.transition(
                        received.id(),
                        new PurchaseOrderStatusRequest(PurchaseOrderStatus.CANCELLED, received.version()),
                        actorId,
                        "127.0.0.1"))
                .hasMessageContaining("not allowed");
    }

    @Test
    void simultaneousFinalReceiptHasOneWinnerAndOneLedgerAndAuditResult() throws Exception {
        InventoryItemResponse oil = item("OIL", "Cooking oil", InventoryUnit.MILLILITER, "100.000");
        SupplierResponse supplier = supplier(oil.id(), "0.0100");
        PurchaseOrderResponse draft = draftWithLine(supplier.id(), oil.id(), "5.000");
        PurchaseOrderResponse ordered = purchaseOrderService.transition(
                draft.id(),
                new PurchaseOrderStatusRequest(PurchaseOrderStatus.ORDERED, draft.version()),
                actorId,
                "127.0.0.1");
        Long lineId = ordered.items().getFirst().id();
        CyclicBarrier barrier = new CyclicBarrier(2);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> attemptReceipt(ordered, lineId, barrier));
            Future<Integer> second = executor.submit(() -> attemptReceipt(ordered, lineId, barrier));
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }

        PurchaseOrderResponse current = purchaseOrderService.get(ordered.id());
        assertThat(current.status()).isEqualTo(PurchaseOrderStatus.RECEIVED);
        assertThat(current.items().getFirst().receivedQuantity()).isEqualByComparingTo("5.000");
        assertThat(current.items().getFirst().remainingQuantity()).isEqualByComparingTo("0.000");
        assertThat(count("SELECT COUNT(*) FROM stock_movements WHERE reference_type = 'PURCHASE_ORDER_ITEM'"))
                .isEqualTo(1);
        assertThat(quantity(
                        "SELECT SUM(quantity) FROM stock_movements WHERE reference_type = 'PURCHASE_ORDER_ITEM'"))
                .isEqualByComparingTo("5.000");
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE action = 'STOCK_RECEIPT_RECORDED'"))
                .isEqualTo(1);
    }

    private boolean attemptKitchenStart(KitchenFixture fixture, CyclicBarrier barrier) {
        await(barrier);
        try {
            kitchenService.transitionItem(
                    fixture.ticketId(),
                    fixture.kitchenItemId(),
                    new KitchenItemStatusRequest(KitchenItemStatus.PREPARING, 0L),
                    actorId,
                    "127.0.0.1");
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private int attemptReceipt(PurchaseOrderResponse order, Long lineId, CyclicBarrier barrier) {
        await(barrier);
        try {
            return mockMvc.perform(post("/api/v1/purchase-orders/{id}/receipts", order.id())
                            .with(jwt()
                                    .jwt(token -> token.subject(actorId.toString())
                                            .claim("roles", List.of("ADMIN")))
                                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"purchaseOrderItemId":%d,"quantity":5,"version":%d}
                                    """.formatted(lineId, order.version())))
                    .andReturn()
                    .getResponse()
                    .getStatus();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private InventoryItemResponse item(String code, String name, InventoryUnit unit, String threshold) {
        return inventoryService.create(
                new InventoryItemRequest(code, name, unit, decimal(threshold), null),
                actorId,
                "127.0.0.1");
    }

    private ManualMovementRequest movement(Long itemId, StockMovementType type, String amount, String reason) {
        return new ManualMovementRequest(itemId, type, decimal(amount), reason);
    }

    private SupplierResponse supplier(Long inventoryItemId, String cost) {
        SupplierResponse supplier = supplierService.create(
                new SupplierRequest("SUP-1", "Primary Supplier", null, null, null, null, null),
                actorId,
                "127.0.0.1");
        supplierService.upsertItem(
                supplier.id(),
                new SupplierItemRequest(inventoryItemId, "SKU-1", decimal(cost), true, null),
                actorId,
                "127.0.0.1");
        return supplierService.get(supplier.id());
    }

    private PurchaseOrderResponse draftWithLine(Long supplierId, Long itemId, String quantity) {
        PurchaseOrderResponse draft = purchaseOrderService.create(
                new PurchaseOrderRequest(supplierId, "Test purchase", null),
                actorId,
                "127.0.0.1");
        return purchaseOrderService.addLine(
                draft.id(),
                new PurchaseOrderLineRequest(itemId, decimal(quantity), draft.version()),
                actorId,
                "127.0.0.1");
    }

    private KitchenFixture kitchenFixture(int quantity) {
        jdbcTemplate.update(
                "INSERT INTO restaurant_tables "
                        + "(table_number, display_name, capacity, section, status, active) "
                        + "VALUES ('I-1', 'Inventory test', 4, 'Main', 'AVAILABLE', TRUE)");
        Long tableId = id("SELECT id FROM restaurant_tables WHERE table_number = 'I-1'");
        jdbcTemplate.update("INSERT INTO menu_categories (name, display_order, active) VALUES ('Food', 0, TRUE)");
        Long categoryId = id("SELECT id FROM menu_categories WHERE name = 'Food'");
        jdbcTemplate.update(
                "INSERT INTO menu_items "
                        + "(category_id, code, name, base_price, display_order, active, available_for_sale) "
                        + "VALUES (?, 'BURGER', 'Burger', 10.00, 0, TRUE, TRUE)",
                categoryId);
        Long menuItemId = id("SELECT id FROM menu_items WHERE code = 'BURGER'");
        jdbcTemplate.update(
                "INSERT INTO modifier_groups "
                        + "(name, selection_type, minimum_selections, maximum_selections, display_order, active) "
                        + "VALUES ('Extras', 'MULTIPLE', 0, 2, 0, TRUE)");
        Long groupId = id("SELECT id FROM modifier_groups WHERE name = 'Extras'");
        jdbcTemplate.update(
                "INSERT INTO modifier_options "
                        + "(modifier_group_id, name, price_adjustment, display_order, active) "
                        + "VALUES (?, 'Extra cheese', 1.00, 0, TRUE)",
                groupId);
        Long optionId = id("SELECT id FROM modifier_options WHERE name = 'Extra cheese'");
        jdbcTemplate.update(
                "INSERT INTO orders "
                        + "(order_number, restaurant_table_id, status, subtotal, total, submitted_at) "
                        + "VALUES ('ORD-INVENTORY', ?, 'SUBMITTED', 20.00, 20.00, CURRENT_TIMESTAMP(6))",
                tableId);
        Long orderId = id("SELECT id FROM orders WHERE order_number = 'ORD-INVENTORY'");
        jdbcTemplate.update(
                "INSERT INTO order_items "
                        + "(order_id, menu_item_id, item_code_snapshot, item_name_snapshot, "
                        + "base_price_snapshot, quantity, unit_total_snapshot, line_total, display_order) "
                        + "VALUES (?, ?, 'BURGER', 'Burger', 10.00, ?, 11.00, 22.00, 0)",
                orderId,
                menuItemId,
                quantity);
        Long orderItemId = id("SELECT id FROM order_items WHERE order_id = " + orderId);
        jdbcTemplate.update(
                "INSERT INTO order_item_modifiers "
                        + "(order_item_id, modifier_group_id, modifier_option_id, group_name_snapshot, "
                        + "option_name_snapshot, price_adjustment_snapshot, display_order) "
                        + "VALUES (?, ?, ?, 'Extras', 'Extra cheese', 1.00, 0)",
                orderItemId,
                groupId,
                optionId);
        jdbcTemplate.update("INSERT INTO kitchen_tickets (order_id, status) VALUES (?, 'QUEUED')", orderId);
        Long ticketId = id("SELECT id FROM kitchen_tickets WHERE order_id = " + orderId);
        jdbcTemplate.update(
                "INSERT INTO kitchen_ticket_items (kitchen_ticket_id, order_item_id, status) "
                        + "VALUES (?, ?, 'QUEUED')",
                ticketId,
                orderItemId);
        Long kitchenItemId = id("SELECT id FROM kitchen_ticket_items WHERE order_item_id = " + orderItemId);
        return new KitchenFixture(menuItemId, optionId, ticketId, kitchenItemId);
    }

    private long count(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, Long.class, arguments);
    }

    private long number(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, Long.class, arguments);
    }

    private Long id(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    private BigDecimal quantity(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, BigDecimal.class, arguments);
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record KitchenFixture(
            Long menuItemId,
            Long modifierOptionId,
            Long ticketId,
            Long kitchenItemId) {
    }
}
