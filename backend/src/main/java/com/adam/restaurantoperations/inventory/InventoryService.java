package com.adam.restaurantoperations.inventory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.adam.restaurantoperations.audit.InventoryAuditService;
import com.adam.restaurantoperations.inventory.InventoryDtos.InventoryItemRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.InventoryItemResponse;
import com.adam.restaurantoperations.inventory.InventoryDtos.ManualMovementRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.StockMovementResponse;
import com.adam.restaurantoperations.users.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private static final int QUANTITY_SCALE = 3;

    private final InventoryItemRepository items;
    private final StockMovementRepository movements;
    private final UserRepository users;
    private final InventoryAuditService audit;

    public InventoryService(
            InventoryItemRepository items,
            StockMovementRepository movements,
            UserRepository users,
            InventoryAuditService audit) {
        this.items = items;
        this.movements = movements;
        this.users = users;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<InventoryItemResponse> list(
            Boolean active,
            Boolean lowStock,
            InventoryUnit unit,
            String search,
            String sortBy,
            Sort.Direction direction) {
        Comparator<InventoryItemResponse> comparator = comparator(sortBy);
        if (direction == Sort.Direction.DESC) {
            comparator = comparator.reversed();
        }
        return items.findAllByOrderByNameAscIdAsc().stream()
                .map(this::response)
                .filter(item -> active == null || item.active() == active)
                .filter(item -> lowStock == null || item.lowStock() == lowStock)
                .filter(item -> unit == null || item.unit() == unit)
                .filter(item -> matches(item, search))
                .sorted(comparator.thenComparing(InventoryItemResponse::id))
                .toList();
    }

    @Transactional(readOnly = true)
    public InventoryItemResponse get(Long id) {
        return response(find(id));
    }

    @Transactional
    public InventoryItemResponse create(
            InventoryItemRequest request,
            Long actorId,
            String ipAddress) {
        String code = code(request.code());
        String name = text(request.name());
        ensureUnique(code, name, null);
        InventoryItemEntity saved = save(new InventoryItemEntity(
                code,
                name,
                request.unit(),
                quantity(request.reorderThreshold())));
        audit.record(
                "INVENTORY_ITEM_CREATED",
                actorId,
                "INVENTORY_ITEM",
                saved.getId(),
                Map.of("code", saved.getCode()),
                ipAddress);
        return response(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InventoryItemResponse update(
            Long id,
            InventoryItemRequest request,
            Long actorId,
            String ipAddress) {
        InventoryItemEntity item = find(id);
        verifyVersion(item, request.version());
        if (item.getUnit() != request.unit()) {
            throw InventoryManagementException.conflict(
                    "An inventory item's canonical unit cannot be changed");
        }
        String code = code(request.code());
        String name = text(request.name());
        ensureUnique(code, name, id);
        item.update(code, name, quantity(request.reorderThreshold()));
        InventoryItemEntity saved = save(item);
        audit.record(
                "INVENTORY_ITEM_UPDATED",
                actorId,
                "INVENTORY_ITEM",
                id,
                Map.of("code", saved.getCode()),
                ipAddress);
        return response(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public InventoryItemResponse setActivation(
            Long id,
            boolean value,
            Long version,
            Long actorId,
            String ipAddress) {
        InventoryItemEntity item = find(id);
        verifyVersion(item, version);
        item.setActive(value);
        InventoryItemEntity saved = save(item);
        audit.record(
                value ? "INVENTORY_ITEM_ACTIVATED" : "INVENTORY_ITEM_DEACTIVATED",
                actorId,
                "INVENTORY_ITEM",
                id,
                Map.of("active", value),
                ipAddress);
        return response(saved);
    }

    @Transactional
    public StockMovementResponse recordManualMovement(
            ManualMovementRequest request,
            Long actorId,
            String ipAddress) {
        if (!request.movementType().manual()) {
            throw InventoryManagementException.badRequest(
                    "Manual stock movements must be ADJUSTMENT_IN, ADJUSTMENT_OUT, or WASTE");
        }
        InventoryItemEntity item = find(request.inventoryItemId());
        StockMovementEntity movement = movements.saveAndFlush(new StockMovementEntity(
                item,
                request.movementType(),
                quantity(request.quantity()),
                Instant.now(),
                users.getReferenceById(actorId),
                "MANUAL",
                null,
                null,
                optional(request.reason()),
                null,
                null));
        audit.record(
                auditAction(request.movementType()),
                actorId,
                "STOCK_MOVEMENT",
                movement.getId(),
                Map.of("inventoryItemId", item.getId(), "movementType", request.movementType().name()),
                ipAddress);
        return movementResponse(movement);
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponse> movements(Long itemId) {
        find(itemId);
        return movements.findByInventoryItemIdOrderByOccurredAtDescIdDesc(itemId).stream()
                .map(this::movementResponse)
                .toList();
    }

    private InventoryItemResponse response(InventoryItemEntity item) {
        BigDecimal onHand = quantity(movements.balance(item.getId()));
        return new InventoryItemResponse(
                item.getId(),
                item.getCode(),
                item.getName(),
                item.getUnit(),
                item.getReorderThreshold(),
                onHand,
                onHand.compareTo(item.getReorderThreshold()) <= 0,
                item.isActive(),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                item.getVersion());
    }

    private StockMovementResponse movementResponse(StockMovementEntity movement) {
        BigDecimal signed = movement.getQuantity().multiply(BigDecimal.valueOf(movement.getMovementType().sign()));
        return new StockMovementResponse(
                movement.getId(),
                movement.getInventoryItem().getId(),
                movement.getInventoryItem().getCode(),
                movement.getInventoryItem().getName(),
                movement.getInventoryItem().getUnit(),
                movement.getMovementType(),
                movement.getQuantity(),
                signed,
                movement.getOccurredAt(),
                movement.getReferenceType(),
                movement.getReferenceId(),
                movement.getReason(),
                movement.getUnitCost(),
                movement.getTotalCost());
    }

    private Comparator<InventoryItemResponse> comparator(String sortBy) {
        return switch (sortBy == null ? "name" : sortBy) {
            case "code" -> Comparator.comparing(InventoryItemResponse::code, String.CASE_INSENSITIVE_ORDER);
            case "onHand" -> Comparator.comparing(InventoryItemResponse::onHand);
            case "reorderThreshold" -> Comparator.comparing(InventoryItemResponse::reorderThreshold);
            case "updatedAt" -> Comparator.comparing(InventoryItemResponse::updatedAt);
            default -> Comparator.comparing(InventoryItemResponse::name, String.CASE_INSENSITIVE_ORDER);
        };
    }

    private boolean matches(InventoryItemResponse item, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.trim().toLowerCase(Locale.ROOT);
        return item.code().toLowerCase(Locale.ROOT).contains(term)
                || item.name().toLowerCase(Locale.ROOT).contains(term);
    }

    private void ensureUnique(String code, String name, Long id) {
        boolean duplicateCode = id == null
                ? items.existsByCodeIgnoreCase(code)
                : items.existsByCodeIgnoreCaseAndIdNot(code, id);
        boolean duplicateName = id == null
                ? items.existsByNameIgnoreCase(name)
                : items.existsByNameIgnoreCaseAndIdNot(name, id);
        if (duplicateCode || duplicateName) {
            throw InventoryManagementException.conflict("Inventory item code and name must be unique");
        }
    }

    private InventoryItemEntity find(Long id) {
        return items.findById(id).orElseThrow(() -> InventoryManagementException.notFound("Inventory item"));
    }

    private InventoryItemEntity save(InventoryItemEntity item) {
        try {
            return items.saveAndFlush(item);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw InventoryManagementException.stale();
        } catch (DataIntegrityViolationException exception) {
            throw InventoryManagementException.conflict("Inventory item code and name must be unique");
        }
    }

    private void verifyVersion(InventoryItemEntity item, Long suppliedVersion) {
        if (suppliedVersion == null || item.getVersion() != suppliedVersion) {
            throw InventoryManagementException.stale();
        }
    }

    static BigDecimal quantity(BigDecimal value) {
        return value.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    static String code(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    static String text(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String auditAction(StockMovementType type) {
        return switch (type) {
            case ADJUSTMENT_IN -> "STOCK_ADJUSTMENT_IN";
            case ADJUSTMENT_OUT -> "STOCK_ADJUSTMENT_OUT";
            case WASTE -> "STOCK_WASTE_RECORDED";
            default -> throw InventoryManagementException.badRequest("Movement type is not manual");
        };
    }
}
