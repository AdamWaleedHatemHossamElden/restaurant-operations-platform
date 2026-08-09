package com.adam.restaurantoperations.orders;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.adam.restaurantoperations.audit.OrderAuditService;
import com.adam.restaurantoperations.auth.service.RequestMetadata;
import com.adam.restaurantoperations.menu.MenuCategoryEntity;
import com.adam.restaurantoperations.menu.MenuCategoryRepository;
import com.adam.restaurantoperations.menu.MenuItemEntity;
import com.adam.restaurantoperations.menu.MenuItemModifierGroupEntity;
import com.adam.restaurantoperations.menu.MenuItemModifierGroupRepository;
import com.adam.restaurantoperations.menu.MenuItemRepository;
import com.adam.restaurantoperations.menu.ModifierGroupEntity;
import com.adam.restaurantoperations.menu.ModifierGroupRepository;
import com.adam.restaurantoperations.menu.ModifierOptionEntity;
import com.adam.restaurantoperations.menu.ModifierOptionRepository;
import com.adam.restaurantoperations.orders.OrderDtos.AddOrderItemRequest;
import com.adam.restaurantoperations.orders.OrderDtos.CreateOrderRequest;
import com.adam.restaurantoperations.orders.OrderDtos.ModifierSelection;
import com.adam.restaurantoperations.orders.OrderDtos.ModifierSnapshotResponse;
import com.adam.restaurantoperations.orders.OrderDtos.OrderItemResponse;
import com.adam.restaurantoperations.orders.OrderDtos.OrderResponse;
import com.adam.restaurantoperations.orders.OrderDtos.OrderStatusRequest;
import com.adam.restaurantoperations.orders.OrderDtos.ReservationSummary;
import com.adam.restaurantoperations.orders.OrderDtos.StatusHistoryResponse;
import com.adam.restaurantoperations.orders.OrderDtos.TableSummary;
import com.adam.restaurantoperations.orders.OrderDtos.UpdateOrderItemRequest;
import com.adam.restaurantoperations.orders.OrderDtos.UpdateOrderRequest;
import com.adam.restaurantoperations.reservations.ReservationEntity;
import com.adam.restaurantoperations.reservations.ReservationRepository;
import com.adam.restaurantoperations.reservations.ReservationStatus;
import com.adam.restaurantoperations.tables.RestaurantTableEntity;
import com.adam.restaurantoperations.tables.RestaurantTableRepository;
import com.adam.restaurantoperations.tables.TableStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "orderNumber", "status", "total");
    private static final int MONEY_SCALE = 2;

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final OrderItemModifierRepository orderModifiers;
    private final OrderStatusHistoryRepository history;
    private final RestaurantTableRepository tables;
    private final ReservationRepository reservations;
    private final MenuCategoryRepository categories;
    private final MenuItemRepository menuItems;
    private final MenuItemModifierGroupRepository assignments;
    private final ModifierGroupRepository groups;
    private final ModifierOptionRepository options;
    private final OrderNumberGenerator numberGenerator;
    private final OrderAuditService audit;

    public OrderService(
            OrderRepository orders,
            OrderItemRepository orderItems,
            OrderItemModifierRepository orderModifiers,
            OrderStatusHistoryRepository history,
            RestaurantTableRepository tables,
            ReservationRepository reservations,
            MenuCategoryRepository categories,
            MenuItemRepository menuItems,
            MenuItemModifierGroupRepository assignments,
            ModifierGroupRepository groups,
            ModifierOptionRepository options,
            OrderNumberGenerator numberGenerator,
            OrderAuditService audit) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.orderModifiers = orderModifiers;
        this.history = history;
        this.tables = tables;
        this.reservations = reservations;
        this.categories = categories;
        this.menuItems = menuItems;
        this.assignments = assignments;
        this.groups = groups;
        this.options = options;
        this.numberGenerator = numberGenerator;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(
            OrderStatus status,
            Long tableId,
            Long reservationId,
            String orderNumber,
            Instant createdFrom,
            Instant createdTo,
            String sortBy,
            Sort.Direction direction) {
        validateRange(createdFrom, createdTo);
        String field = SORTABLE_FIELDS.contains(sortBy) ? sortBy : "createdAt";
        Sort sort = Sort.by(direction, field).and(Sort.by(Sort.Direction.ASC, "id"));
        return orders.findAll(
                        filters(status, tableId, reservationId, orderNumber, createdFrom, createdTo),
                        sort)
                .stream()
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long id) {
        return response(find(id));
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request, Long actorId, RequestMetadata metadata) {
        TableReservation selection = validateTableAndReservation(
                request.restaurantTableId(), request.reservationId());
        OrderEntity order = new OrderEntity(
                uniqueOrderNumber(),
                selection.table(),
                selection.reservation(),
                optional(request.notes()));
        OrderEntity saved = save(order);
        history.save(new OrderStatusHistoryEntity(saved, null, OrderStatus.OPEN, actorId));
        audit.record(
                "ORDER_CREATED",
                actorId,
                saved.getId(),
                saved.getOrderNumber(),
                selection.table().getId(),
                metadata.ipAddress());
        return response(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse update(
            Long id,
            UpdateOrderRequest request,
            Long actorId,
            RequestMetadata metadata) {
        OrderEntity order = lockedOrder(id);
        verifyMutableAndVersion(order, request.version());
        TableReservation selection = validateTableAndReservation(
                request.restaurantTableId(), request.reservationId());
        order.updateMetadata(selection.table(), selection.reservation(), optional(request.notes()));
        OrderEntity saved = save(order);
        audit.record(
                "ORDER_UPDATED",
                actorId,
                saved.getId(),
                saved.getOrderNumber(),
                selection.table().getId(),
                metadata.ipAddress());
        return response(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse addItem(
            Long orderId,
            AddOrderItemRequest request,
            Long actorId,
            RequestMetadata metadata) {
        OrderEntity order = lockedOrder(orderId);
        verifyMutableAndVersion(order, request.version());
        PricePlan plan = price(request.menuItemId(), request.modifierSelections());
        int displayOrder = orderItems.maximumDisplayOrder(orderId) + 1;
        BigDecimal lineTotal = multiply(plan.unitTotal(), request.quantity());
        OrderItemEntity item = new OrderItemEntity(
                order,
                plan.item(),
                plan.item().getCode(),
                plan.item().getName(),
                money(plan.item().getBasePrice()),
                request.quantity(),
                optional(request.notes()),
                plan.unitTotal(),
                lineTotal,
                displayOrder);
        OrderItemEntity savedItem = orderItems.saveAndFlush(item);
        saveModifierSnapshots(savedItem, plan.modifiers());
        recalculate(order);
        OrderEntity savedOrder = save(order);
        audit.record(
                "ORDER_ITEM_ADDED",
                actorId,
                orderId,
                savedOrder.getOrderNumber(),
                savedItem.getId(),
                metadata.ipAddress());
        return response(savedOrder);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse updateItem(
            Long orderId,
            Long itemId,
            UpdateOrderItemRequest request,
            Long actorId,
            RequestMetadata metadata) {
        OrderEntity order = lockedOrder(orderId);
        verifyMutableAndVersion(order, request.version());
        OrderItemEntity item = orderItems.findByIdAndOrderId(itemId, orderId)
                .orElseThrow(() -> OrderManagementException.notFound("Order item"));
        if (request.modifierSelections() == null) {
            item.updateWithoutRepricing(
                    request.quantity(),
                    optional(request.notes()),
                    multiply(item.getUnitTotalSnapshot(), request.quantity()));
        } else {
            PricePlan plan = price(item.getMenuItem().getId(), request.modifierSelections());
            item.replaceSnapshot(
                    plan.item().getCode(),
                    plan.item().getName(),
                    money(plan.item().getBasePrice()),
                    request.quantity(),
                    optional(request.notes()),
                    plan.unitTotal(),
                    multiply(plan.unitTotal(), request.quantity()));
            orderModifiers.deleteByOrderItemId(itemId);
            orderModifiers.flush();
            orderItems.saveAndFlush(item);
            saveModifierSnapshots(item, plan.modifiers());
        }
        orderItems.saveAndFlush(item);
        recalculate(order);
        OrderEntity savedOrder = save(order);
        audit.record(
                "ORDER_ITEM_UPDATED",
                actorId,
                orderId,
                savedOrder.getOrderNumber(),
                itemId,
                metadata.ipAddress());
        return response(savedOrder);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse removeItem(
            Long orderId,
            Long itemId,
            Long version,
            Long actorId,
            RequestMetadata metadata) {
        OrderEntity order = lockedOrder(orderId);
        verifyMutableAndVersion(order, version);
        OrderItemEntity item = orderItems.findByIdAndOrderId(itemId, orderId)
                .orElseThrow(() -> OrderManagementException.notFound("Order item"));
        orderItems.delete(item);
        orderItems.flush();
        recalculate(order);
        OrderEntity savedOrder = save(order);
        audit.record(
                "ORDER_ITEM_REMOVED",
                actorId,
                orderId,
                savedOrder.getOrderNumber(),
                itemId,
                metadata.ipAddress());
        return response(savedOrder);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse transition(
            Long orderId,
            OrderStatusRequest request,
            Long actorId,
            RequestMetadata metadata) {
        OrderEntity order = lockedOrder(orderId);
        verifyVersion(order, request.version());
        OrderStatus previous = order.getStatus();
        if (!previous.canTransitionTo(request.status())) {
            throw OrderManagementException.conflict("Order status transition is not allowed");
        }
        if (request.status() == OrderStatus.SUBMITTED && orderItems.countByOrderId(orderId) == 0) {
            throw OrderManagementException.conflict("An order requires at least one item before submission");
        }
        order.transitionTo(request.status(), Instant.now());
        OrderEntity saved = save(order);
        history.save(new OrderStatusHistoryEntity(saved, previous, request.status(), actorId));
        audit.record(
                auditAction(request.status()),
                actorId,
                orderId,
                saved.getOrderNumber(),
                null,
                metadata.ipAddress());
        return response(saved);
    }

    private PricePlan price(Long menuItemId, List<ModifierSelection> requestedSelections) {
        MenuItemEntity item = menuItems.findByIdForOrderPricing(menuItemId)
                .orElseThrow(() -> OrderManagementException.notFound("Menu item"));
        MenuCategoryEntity category = categories.findByIdForOrderPricing(item.getCategory().getId())
                .orElseThrow(() -> OrderManagementException.notFound("Menu category"));
        if (!category.isActive() || !item.isActive() || !item.isAvailableForSale()) {
            throw OrderManagementException.conflict("Menu item is not currently available for sale");
        }

        Map<Long, List<Long>> selections = validateSelectionShape(requestedSelections);
        List<MenuItemModifierGroupEntity> currentAssignments = assignments.findOrderedByMenuItemId(menuItemId);
        Set<Long> assignedIds = currentAssignments.stream()
                .map(assignment -> assignment.getModifierGroup().getId())
                .collect(java.util.stream.Collectors.toSet());
        if (!assignedIds.containsAll(selections.keySet())) {
            throw OrderManagementException.conflict("A selected modifier group is not assigned to this item");
        }
        Map<Long, ModifierGroupEntity> lockedGroups = lockGroups(assignedIds);
        List<ModifierPlan> modifierPlans = new ArrayList<>();
        Set<Long> selectedOptionIds = new HashSet<>();

        for (MenuItemModifierGroupEntity assignment : currentAssignments) {
            ModifierGroupEntity group = lockedGroups.get(assignment.getModifierGroup().getId());
            List<Long> optionIds = selections.getOrDefault(group.getId(), List.of());
            if (!group.isActive()) {
                if (!optionIds.isEmpty()) {
                    throw OrderManagementException.conflict("Inactive modifier groups cannot be selected");
                }
                continue;
            }
            long activeOptionCount = options.countByGroupIdAndActiveTrue(group.getId());
            if (activeOptionCount < group.getMaximumSelections()) {
                throw OrderManagementException.conflict("Current modifier configuration is not usable");
            }
            if (optionIds.size() < group.getMinimumSelections()
                    || optionIds.size() > group.getMaximumSelections()) {
                throw OrderManagementException.conflict("Modifier selections do not satisfy current rules");
            }
            List<ModifierOptionEntity> selected = new ArrayList<>();
            optionIds.stream().sorted().forEach(optionId -> {
                if (!selectedOptionIds.add(optionId)) {
                    throw OrderManagementException.badRequest("A modifier option may be selected only once");
                }
                ModifierOptionEntity option = options.findByIdForOrderPricing(optionId)
                        .orElseThrow(() -> OrderManagementException.notFound("Modifier option"));
                if (!option.getGroup().getId().equals(group.getId())) {
                    throw OrderManagementException.conflict("A selected modifier option belongs to another group");
                }
                if (!option.isActive()) {
                    throw OrderManagementException.conflict("Inactive modifier options cannot be selected");
                }
                selected.add(option);
            });
            selected.sort(Comparator.comparingInt(ModifierOptionEntity::getDisplayOrder)
                    .thenComparing(ModifierOptionEntity::getId));
            for (ModifierOptionEntity option : selected) {
                modifierPlans.add(new ModifierPlan(group, option, assignment.getDisplayOrder()));
            }
        }

        BigDecimal unitTotal = money(item.getBasePrice());
        for (ModifierPlan modifier : modifierPlans) {
            unitTotal = money(unitTotal.add(modifier.option().getPriceAdjustment()));
        }
        return new PricePlan(item, modifierPlans, unitTotal);
    }

    private Map<Long, List<Long>> validateSelectionShape(List<ModifierSelection> requestedSelections) {
        Map<Long, List<Long>> selections = new HashMap<>();
        Set<Long> allOptions = new HashSet<>();
        for (ModifierSelection selection : requestedSelections) {
            if (selections.putIfAbsent(selection.modifierGroupId(), selection.optionIds()) != null) {
                throw OrderManagementException.badRequest("A modifier group may be supplied only once");
            }
            for (Long optionId : selection.optionIds()) {
                if (!allOptions.add(optionId)) {
                    throw OrderManagementException.badRequest("A modifier option may be selected only once");
                }
            }
        }
        return selections;
    }

    private Map<Long, ModifierGroupEntity> lockGroups(Set<Long> groupIds) {
        Map<Long, ModifierGroupEntity> locked = new HashMap<>();
        groupIds.stream().sorted().forEach(groupId -> locked.put(
                groupId,
                groups.findByIdForUpdate(groupId)
                        .orElseThrow(() -> OrderManagementException.notFound("Modifier group"))));
        return locked;
    }

    private void saveModifierSnapshots(OrderItemEntity item, List<ModifierPlan> modifierPlans) {
        List<OrderItemModifierEntity> snapshots = new ArrayList<>();
        int displayOrder = 0;
        for (ModifierPlan modifier : modifierPlans) {
            snapshots.add(new OrderItemModifierEntity(
                    item,
                    modifier.group(),
                    modifier.option(),
                    modifier.group().getName(),
                    modifier.option().getName(),
                    money(modifier.option().getPriceAdjustment()),
                    displayOrder++));
        }
        orderModifiers.saveAll(snapshots);
        orderModifiers.flush();
    }

    private void recalculate(OrderEntity order) {
        BigDecimal subtotal = orderItems.findByOrderIdOrderByDisplayOrderAscIdAsc(order.getId()).stream()
                .map(OrderItemEntity::getLineTotal)
                .reduce(BigDecimal.ZERO.setScale(MONEY_SCALE), BigDecimal::add);
        order.updateTotals(money(subtotal));
        order.touch();
    }

    private TableReservation validateTableAndReservation(Long tableId, Long reservationId) {
        RestaurantTableEntity table = tables.findByIdForReservationUpdate(tableId)
                .orElseThrow(() -> OrderManagementException.notFound("Restaurant table"));
        if (!table.isActive() || table.getStatus() != TableStatus.AVAILABLE) {
            throw OrderManagementException.conflict("Restaurant table is not operationally available");
        }
        if (reservationId == null) {
            return new TableReservation(table, null);
        }
        ReservationEntity reservation = reservations.findByIdForOrderLink(reservationId)
                .orElseThrow(() -> OrderManagementException.notFound("Reservation"));
        if (reservation.getStatus() != ReservationStatus.SEATED) {
            throw OrderManagementException.conflict("Only a SEATED reservation may be linked to an order");
        }
        if (reservation.getRestaurantTable() == null
                || !reservation.getRestaurantTable().getId().equals(table.getId())) {
            throw OrderManagementException.conflict("Reservation table must match the order table");
        }
        return new TableReservation(table, reservation);
    }

    private OrderResponse response(OrderEntity order) {
        List<OrderItemResponse> itemResponses = orderItems
                .findByOrderIdOrderByDisplayOrderAscIdAsc(order.getId())
                .stream()
                .map(this::itemResponse)
                .toList();
        List<StatusHistoryResponse> historyResponses = history
                .findByOrderIdOrderByChangedAtAscIdAsc(order.getId())
                .stream()
                .map(entry -> new StatusHistoryResponse(
                        entry.getId(),
                        entry.getFromStatus(),
                        entry.getToStatus(),
                        entry.getChangedAt(),
                        entry.getChangedByUserId()))
                .toList();
        RestaurantTableEntity table = order.getRestaurantTable();
        ReservationEntity reservation = order.getReservation();
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getVersion(),
                new TableSummary(table.getId(), table.getTableNumber(), table.getDisplayName(), table.getSection()),
                reservation == null
                        ? null
                        : new ReservationSummary(
                                reservation.getId(),
                                reservation.getReservationCode(),
                                reservation.getGuestName()),
                order.getNotes(),
                money(order.getSubtotal()).toPlainString(),
                money(order.getTotal()).toPlainString(),
                itemResponses.size(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getSubmittedAt(),
                order.getCompletedAt(),
                order.getCancelledAt(),
                itemResponses,
                historyResponses);
    }

    private OrderItemResponse itemResponse(OrderItemEntity item) {
        List<ModifierSnapshotResponse> modifiers = orderModifiers
                .findByOrderItemIdOrderByDisplayOrderAscIdAsc(item.getId())
                .stream()
                .map(modifier -> new ModifierSnapshotResponse(
                        modifier.getId(),
                        modifier.getModifierGroup().getId(),
                        modifier.getModifierOption().getId(),
                        modifier.getGroupNameSnapshot(),
                        modifier.getOptionNameSnapshot(),
                        money(modifier.getPriceAdjustmentSnapshot()).toPlainString(),
                        modifier.getDisplayOrder()))
                .toList();
        return new OrderItemResponse(
                item.getId(),
                item.getMenuItem().getId(),
                item.getItemCodeSnapshot(),
                item.getItemNameSnapshot(),
                money(item.getBasePriceSnapshot()).toPlainString(),
                item.getQuantity(),
                item.getNotes(),
                money(item.getUnitTotalSnapshot()).toPlainString(),
                money(item.getLineTotal()).toPlainString(),
                item.getDisplayOrder(),
                modifiers,
                item.getCreatedAt(),
                item.getUpdatedAt());
    }

    private Specification<OrderEntity> filters(
            OrderStatus status,
            Long tableId,
            Long reservationId,
            String orderNumber,
            Instant createdFrom,
            Instant createdTo) {
        return (root, query, builder) -> {
            Predicate predicate = builder.conjunction();
            if (status != null) {
                predicate = builder.and(predicate, builder.equal(root.get("status"), status));
            }
            if (tableId != null) {
                predicate = builder.and(predicate, builder.equal(root.get("restaurantTable").get("id"), tableId));
            }
            if (reservationId != null) {
                predicate = builder.and(predicate, builder.equal(root.get("reservation").get("id"), reservationId));
            }
            if (hasText(orderNumber)) {
                String term = "%" + orderNumber.trim().toLowerCase(Locale.ROOT) + "%";
                predicate = builder.and(predicate, builder.like(builder.lower(root.get("orderNumber")), term));
            }
            if (createdFrom != null) {
                predicate = builder.and(predicate, builder.greaterThanOrEqualTo(root.get("createdAt"), createdFrom));
            }
            if (createdTo != null) {
                predicate = builder.and(predicate, builder.lessThan(root.get("createdAt"), createdTo));
            }
            return predicate;
        };
    }

    private String uniqueOrderNumber() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = numberGenerator.generate();
            if (!orders.existsByOrderNumber(candidate)) {
                return candidate;
            }
        }
        throw OrderManagementException.conflict("Order number conflict; retry");
    }

    private OrderEntity lockedOrder(Long id) {
        return orders.findByIdForUpdate(id).orElseThrow(() -> OrderManagementException.notFound("Order"));
    }

    private OrderEntity find(Long id) {
        return orders.findById(id).orElseThrow(() -> OrderManagementException.notFound("Order"));
    }

    private OrderEntity save(OrderEntity order) {
        try {
            return orders.saveAndFlush(order);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw OrderManagementException.stale();
        } catch (DataIntegrityViolationException exception) {
            throw OrderManagementException.conflict("Order data conflicts with another request; retry");
        }
    }

    private void verifyMutableAndVersion(OrderEntity order, Long suppliedVersion) {
        verifyVersion(order, suppliedVersion);
        if (!order.getStatus().isMutable()) {
            throw OrderManagementException.immutable();
        }
    }

    private void verifyVersion(OrderEntity order, Long suppliedVersion) {
        if (order.getVersion() != suppliedVersion) {
            throw OrderManagementException.stale();
        }
    }

    private String auditAction(OrderStatus status) {
        return switch (status) {
            case SUBMITTED -> "ORDER_SUBMITTED";
            case COMPLETED -> "ORDER_COMPLETED";
            case CANCELLED -> "ORDER_CANCELLED";
            case OPEN -> throw OrderManagementException.conflict("Order status transition is not allowed");
        };
    }

    private void validateRange(Instant createdFrom, Instant createdTo) {
        if (createdFrom != null && createdTo != null && !createdFrom.isBefore(createdTo)) {
            throw OrderManagementException.badRequest("Start of range must precede end of range");
        }
    }

    private BigDecimal multiply(BigDecimal unit, int quantity) {
        return money(unit.multiply(BigDecimal.valueOf(quantity)));
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private String optional(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record TableReservation(RestaurantTableEntity table, ReservationEntity reservation) {
    }

    private record ModifierPlan(
            ModifierGroupEntity group,
            ModifierOptionEntity option,
            int groupDisplayOrder) {
    }

    private record PricePlan(
            MenuItemEntity item,
            List<ModifierPlan> modifiers,
            BigDecimal unitTotal) {
    }
}
