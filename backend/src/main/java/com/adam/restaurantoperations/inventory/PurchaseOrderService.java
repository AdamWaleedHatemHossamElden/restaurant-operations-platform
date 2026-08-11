package com.adam.restaurantoperations.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.adam.restaurantoperations.audit.InventoryAuditService;
import com.adam.restaurantoperations.inventory.InventoryDtos.PurchaseOrderLineRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.PurchaseOrderLineResponse;
import com.adam.restaurantoperations.inventory.InventoryDtos.PurchaseOrderLineUpdateRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.PurchaseOrderRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.PurchaseOrderResponse;
import com.adam.restaurantoperations.inventory.InventoryDtos.PurchaseOrderStatusRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.PurchaseReceiptRequest;
import com.adam.restaurantoperations.users.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PurchaseOrderService {
    private static final int COST_SCALE = 4;

    private final PurchaseOrderRepository purchaseOrders;
    private final PurchaseOrderItemRepository lines;
    private final SupplierRepository suppliers;
    private final SupplierInventoryItemRepository supplierItems;
    private final StockMovementRepository movements;
    private final UserRepository users;
    private final PurchaseOrderNumberGenerator numberGenerator;
    private final InventoryAuditService audit;

    public PurchaseOrderService(
            PurchaseOrderRepository purchaseOrders,
            PurchaseOrderItemRepository lines,
            SupplierRepository suppliers,
            SupplierInventoryItemRepository supplierItems,
            StockMovementRepository movements,
            UserRepository users,
            PurchaseOrderNumberGenerator numberGenerator,
            InventoryAuditService audit) {
        this.purchaseOrders = purchaseOrders;
        this.lines = lines;
        this.suppliers = suppliers;
        this.supplierItems = supplierItems;
        this.movements = movements;
        this.users = users;
        this.numberGenerator = numberGenerator;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderResponse> list(
            PurchaseOrderStatus status,
            Long supplierId,
            String search) {
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return purchaseOrders.findAllByOrderByCreatedAtDescIdDesc().stream()
                .filter(order -> status == null || order.getStatus() == status)
                .filter(order -> supplierId == null || order.getSupplier().getId().equals(supplierId))
                .filter(order -> term.isEmpty()
                        || order.getPurchaseOrderNumber().toLowerCase(Locale.ROOT).contains(term)
                        || order.getSupplier().getName().toLowerCase(Locale.ROOT).contains(term))
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse get(Long id) {
        return response(find(id));
    }

    @Transactional
    public PurchaseOrderResponse create(PurchaseOrderRequest request, Long actorId, String ipAddress) {
        SupplierEntity supplier = supplier(request.supplierId());
        if (!supplier.isActive()) {
            throw InventoryManagementException.conflict("Inactive suppliers cannot receive new purchase orders");
        }
        PurchaseOrderEntity saved = save(new PurchaseOrderEntity(
                uniqueNumber(),
                supplier,
                InventoryService.optional(request.notes())));
        audit.record(
                "PURCHASE_ORDER_CREATED",
                actorId,
                "PURCHASE_ORDER",
                saved.getId(),
                Map.of("purchaseOrderNumber", saved.getPurchaseOrderNumber(), "supplierId", supplier.getId()),
                ipAddress);
        return response(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseOrderResponse update(
            Long id,
            PurchaseOrderRequest request,
            Long actorId,
            String ipAddress) {
        PurchaseOrderEntity order = locked(id);
        verifyDraftAndVersion(order, request.version());
        SupplierEntity supplier = supplier(request.supplierId());
        if (!supplier.isActive()) {
            throw InventoryManagementException.conflict("Inactive suppliers cannot receive new purchase orders");
        }
        if (!supplier.getId().equals(order.getSupplier().getId()) && lines.countByPurchaseOrderId(id) > 0) {
            throw InventoryManagementException.conflict(
                    "Clear purchase-order lines before changing the supplier");
        }
        order.updateDraft(supplier, InventoryService.optional(request.notes()));
        PurchaseOrderEntity saved = save(order);
        audit.record(
                "PURCHASE_ORDER_UPDATED",
                actorId,
                "PURCHASE_ORDER",
                id,
                Map.of("supplierId", supplier.getId()),
                ipAddress);
        return response(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseOrderResponse addLine(
            Long orderId,
            PurchaseOrderLineRequest request,
            Long actorId,
            String ipAddress) {
        PurchaseOrderEntity order = locked(orderId);
        verifyDraftAndVersion(order, request.version());
        if (lines.existsByPurchaseOrderIdAndInventoryItemId(orderId, request.inventoryItemId())) {
            throw InventoryManagementException.conflict("Inventory item is already on this purchase order");
        }
        SupplierInventoryItemEntity relationship = supplierItems.findActiveForPricing(
                        order.getSupplier().getId(), request.inventoryItemId())
                .orElseThrow(() -> InventoryManagementException.conflict(
                        "An active supplier-item relationship is required"));
        if (!relationship.getInventoryItem().isActive()) {
            throw InventoryManagementException.conflict("Inactive inventory items cannot be ordered");
        }
        BigDecimal quantity = InventoryService.quantity(request.quantity());
        BigDecimal cost = relationship.getUnitCost().setScale(COST_SCALE, RoundingMode.HALF_UP);
        PurchaseOrderItemEntity line = new PurchaseOrderItemEntity(
                order,
                relationship.getInventoryItem(),
                quantity,
                cost,
                money(quantity.multiply(cost)),
                lines.maximumDisplayOrder(orderId) + 1);
        lines.saveAndFlush(line);
        recalculate(order);
        PurchaseOrderEntity saved = save(order);
        audit.record(
                "PURCHASE_ORDER_UPDATED",
                actorId,
                "PURCHASE_ORDER",
                orderId,
                Map.of("inventoryItemId", relationship.getInventoryItem().getId()),
                ipAddress);
        return response(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseOrderResponse updateLine(
            Long orderId,
            Long lineId,
            PurchaseOrderLineUpdateRequest request,
            Long actorId,
            String ipAddress) {
        PurchaseOrderEntity order = locked(orderId);
        verifyDraftAndVersion(order, request.version());
        PurchaseOrderItemEntity line = lines.findByIdAndPurchaseOrderId(lineId, orderId)
                .orElseThrow(() -> InventoryManagementException.notFound("Purchase-order item"));
        BigDecimal quantity = InventoryService.quantity(request.quantity());
        line.updateQuantity(quantity, money(quantity.multiply(line.getUnitCostSnapshot())));
        lines.saveAndFlush(line);
        recalculate(order);
        PurchaseOrderEntity saved = save(order);
        audit.record(
                "PURCHASE_ORDER_UPDATED",
                actorId,
                "PURCHASE_ORDER",
                orderId,
                Map.of("purchaseOrderItemId", lineId),
                ipAddress);
        return response(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseOrderResponse removeLine(
            Long orderId,
            Long lineId,
            Long version,
            Long actorId,
            String ipAddress) {
        PurchaseOrderEntity order = locked(orderId);
        verifyDraftAndVersion(order, version);
        PurchaseOrderItemEntity line = lines.findByIdAndPurchaseOrderId(lineId, orderId)
                .orElseThrow(() -> InventoryManagementException.notFound("Purchase-order item"));
        lines.delete(line);
        lines.flush();
        recalculate(order);
        PurchaseOrderEntity saved = save(order);
        audit.record(
                "PURCHASE_ORDER_UPDATED",
                actorId,
                "PURCHASE_ORDER",
                orderId,
                Map.of("removedPurchaseOrderItemId", lineId),
                ipAddress);
        return response(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseOrderResponse transition(
            Long id,
            PurchaseOrderStatusRequest request,
            Long actorId,
            String ipAddress) {
        PurchaseOrderEntity order = locked(id);
        verifyVersion(order, request.version());
        Instant now = Instant.now();
        String action;
        if (request.status() == PurchaseOrderStatus.ORDERED && order.getStatus() == PurchaseOrderStatus.DRAFT) {
            validateCanOrder(order);
            order.order(now);
            action = "PURCHASE_ORDER_ORDERED";
        } else if (request.status() == PurchaseOrderStatus.CANCELLED && canCancel(order)) {
            order.cancel(now);
            action = "PURCHASE_ORDER_CANCELLED";
        } else {
            throw InventoryManagementException.conflict("Purchase-order status transition is not allowed");
        }
        PurchaseOrderEntity saved = save(order);
        audit.record(action, actorId, "PURCHASE_ORDER", id, Map.of("status", saved.getStatus().name()), ipAddress);
        return response(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PurchaseOrderResponse receive(
            Long orderId,
            PurchaseReceiptRequest request,
            Long actorId,
            String ipAddress) {
        PurchaseOrderEntity order = locked(orderId);
        verifyVersion(order, request.version());
        if (order.getStatus() != PurchaseOrderStatus.ORDERED
                && order.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw InventoryManagementException.conflict("This purchase order cannot receive stock");
        }
        PurchaseOrderItemEntity line = lines.findByIdAndOrderIdForUpdate(
                        request.purchaseOrderItemId(), orderId)
                .orElseThrow(() -> InventoryManagementException.notFound("Purchase-order item"));
        BigDecimal receivedNow = InventoryService.quantity(request.quantity());
        if (receivedNow.compareTo(line.remainingQuantity()) > 0) {
            throw InventoryManagementException.conflict("Receipt quantity exceeds the remaining quantity");
        }
        line.receive(receivedNow);
        lines.saveAndFlush(line);
        BigDecimal receiptCost = money(receivedNow.multiply(line.getUnitCostSnapshot()));
        StockMovementEntity movement = movements.saveAndFlush(new StockMovementEntity(
                line.getInventoryItem(),
                StockMovementType.RECEIPT,
                receivedNow,
                Instant.now(),
                users.getReferenceById(actorId),
                "PURCHASE_ORDER_ITEM",
                line.getId(),
                null,
                "Purchase order " + order.getPurchaseOrderNumber(),
                line.getUnitCostSnapshot(),
                receiptCost));
        boolean complete = lines.findByPurchaseOrderIdOrderByDisplayOrderAscIdAsc(orderId).stream()
                .allMatch(item -> item.remainingQuantity().compareTo(BigDecimal.ZERO) == 0);
        order.receive(complete, Instant.now());
        PurchaseOrderEntity saved = save(order);
        audit.record(
                "STOCK_RECEIPT_RECORDED",
                actorId,
                "STOCK_MOVEMENT",
                movement.getId(),
                Map.of("purchaseOrderId", orderId, "inventoryItemId", line.getInventoryItem().getId()),
                ipAddress);
        audit.record(
                complete ? "PURCHASE_ORDER_RECEIVED" : "PURCHASE_ORDER_PARTIALLY_RECEIVED",
                actorId,
                "PURCHASE_ORDER",
                orderId,
                Map.of("status", saved.getStatus().name()),
                ipAddress);
        return response(saved);
    }

    private void validateCanOrder(PurchaseOrderEntity order) {
        if (!order.getSupplier().isActive()) {
            throw InventoryManagementException.conflict("Supplier must be active before ordering");
        }
        List<PurchaseOrderItemEntity> orderLines = lines.findByPurchaseOrderIdOrderByDisplayOrderAscIdAsc(
                order.getId());
        if (orderLines.isEmpty()) {
            throw InventoryManagementException.conflict("A purchase order requires at least one item");
        }
        for (PurchaseOrderItemEntity line : orderLines) {
            if (!line.getInventoryItem().isActive()
                    || supplierItems.findActiveForPricing(
                                    order.getSupplier().getId(), line.getInventoryItem().getId())
                            .isEmpty()) {
                throw InventoryManagementException.conflict(
                        "All purchase-order items require active inventory and supplier relationships");
            }
        }
    }

    private boolean canCancel(PurchaseOrderEntity order) {
        return order.getStatus() == PurchaseOrderStatus.DRAFT
                || order.getStatus() == PurchaseOrderStatus.ORDERED
                || order.getStatus() == PurchaseOrderStatus.PARTIALLY_RECEIVED;
    }

    private void recalculate(PurchaseOrderEntity order) {
        BigDecimal subtotal = lines.findByPurchaseOrderIdOrderByDisplayOrderAscIdAsc(order.getId()).stream()
                .map(PurchaseOrderItemEntity::getLineTotal)
                .reduce(BigDecimal.ZERO.setScale(COST_SCALE), BigDecimal::add);
        order.updateTotals(money(subtotal));
    }

    private PurchaseOrderResponse response(PurchaseOrderEntity order) {
        return new PurchaseOrderResponse(
                order.getId(),
                order.getPurchaseOrderNumber(),
                order.getSupplier().getId(),
                order.getSupplier().getCode(),
                order.getSupplier().getName(),
                order.getStatus(),
                order.getNotes(),
                order.getSubtotal(),
                order.getTotal(),
                order.getOrderedAt(),
                order.getReceivedAt(),
                order.getCancelledAt(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getVersion(),
                lines.findByPurchaseOrderIdOrderByDisplayOrderAscIdAsc(order.getId()).stream()
                        .map(this::lineResponse)
                        .toList());
    }

    private PurchaseOrderLineResponse lineResponse(PurchaseOrderItemEntity line) {
        return new PurchaseOrderLineResponse(
                line.getId(),
                line.getInventoryItem().getId(),
                line.getInventoryCodeSnapshot(),
                line.getInventoryNameSnapshot(),
                line.getUnitSnapshot(),
                line.getOrderedQuantity(),
                line.getReceivedQuantity(),
                line.remainingQuantity(),
                line.getUnitCostSnapshot(),
                line.getLineTotal(),
                line.getDisplayOrder());
    }

    private PurchaseOrderEntity find(Long id) {
        return purchaseOrders.findById(id)
                .orElseThrow(() -> InventoryManagementException.notFound("Purchase order"));
    }

    private PurchaseOrderEntity locked(Long id) {
        return purchaseOrders.findByIdForUpdate(id)
                .orElseThrow(() -> InventoryManagementException.notFound("Purchase order"));
    }

    private SupplierEntity supplier(Long id) {
        return suppliers.findById(id).orElseThrow(() -> InventoryManagementException.notFound("Supplier"));
    }

    private PurchaseOrderEntity save(PurchaseOrderEntity order) {
        try {
            return purchaseOrders.saveAndFlush(order);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw InventoryManagementException.stale();
        } catch (DataIntegrityViolationException exception) {
            throw InventoryManagementException.conflict("Purchase-order data conflicts with another request");
        }
    }

    private void verifyDraftAndVersion(PurchaseOrderEntity order, Long version) {
        verifyVersion(order, version);
        if (order.getStatus() != PurchaseOrderStatus.DRAFT) {
            throw InventoryManagementException.conflict("Only DRAFT purchase orders may be edited");
        }
    }

    private void verifyVersion(PurchaseOrderEntity order, Long version) {
        if (version == null || order.getVersion() != version) {
            throw InventoryManagementException.stale();
        }
    }

    private String uniqueNumber() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = numberGenerator.generate();
            if (!purchaseOrders.existsByPurchaseOrderNumber(candidate)) {
                return candidate;
            }
        }
        throw InventoryManagementException.conflict("Purchase-order number conflict; retry");
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(COST_SCALE, RoundingMode.HALF_UP);
    }
}
