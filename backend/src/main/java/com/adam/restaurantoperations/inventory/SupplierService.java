package com.adam.restaurantoperations.inventory;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.adam.restaurantoperations.audit.InventoryAuditService;
import com.adam.restaurantoperations.inventory.InventoryDtos.SupplierItemRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.SupplierItemResponse;
import com.adam.restaurantoperations.inventory.InventoryDtos.SupplierRequest;
import com.adam.restaurantoperations.inventory.InventoryDtos.SupplierResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierService {
    private final SupplierRepository suppliers;
    private final SupplierInventoryItemRepository supplierItems;
    private final InventoryItemRepository inventoryItems;
    private final InventoryAuditService audit;

    public SupplierService(
            SupplierRepository suppliers,
            SupplierInventoryItemRepository supplierItems,
            InventoryItemRepository inventoryItems,
            InventoryAuditService audit) {
        this.suppliers = suppliers;
        this.supplierItems = supplierItems;
        this.inventoryItems = inventoryItems;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<SupplierResponse> list(Boolean active, String search) {
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return suppliers.findAllByOrderByNameAscIdAsc().stream()
                .filter(supplier -> active == null || supplier.isActive() == active)
                .filter(supplier -> term.isEmpty()
                        || supplier.getCode().toLowerCase(Locale.ROOT).contains(term)
                        || supplier.getName().toLowerCase(Locale.ROOT).contains(term))
                .map(this::response)
                .toList();
    }

    @Transactional(readOnly = true)
    public SupplierResponse get(Long id) {
        return response(find(id));
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request, Long actorId, String ipAddress) {
        String code = InventoryService.code(request.code());
        if (suppliers.existsByCodeIgnoreCase(code)) {
            throw InventoryManagementException.conflict("Supplier code must be unique");
        }
        SupplierEntity saved = save(new SupplierEntity(
                code,
                InventoryService.text(request.name()),
                InventoryService.optional(request.contactName()),
                normalizeEmail(request.email()),
                InventoryService.optional(request.phone()),
                InventoryService.optional(request.notes())));
        audit.record(
                "SUPPLIER_CREATED",
                actorId,
                "SUPPLIER",
                saved.getId(),
                Map.of("code", saved.getCode()),
                ipAddress);
        return response(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SupplierResponse update(
            Long id,
            SupplierRequest request,
            Long actorId,
            String ipAddress) {
        SupplierEntity supplier = locked(id);
        verifyVersion(supplier, request.version());
        String code = InventoryService.code(request.code());
        if (suppliers.existsByCodeIgnoreCaseAndIdNot(code, id)) {
            throw InventoryManagementException.conflict("Supplier code must be unique");
        }
        supplier.update(
                code,
                InventoryService.text(request.name()),
                InventoryService.optional(request.contactName()),
                normalizeEmail(request.email()),
                InventoryService.optional(request.phone()),
                InventoryService.optional(request.notes()));
        SupplierEntity saved = save(supplier);
        audit.record(
                "SUPPLIER_UPDATED",
                actorId,
                "SUPPLIER",
                id,
                Map.of("code", saved.getCode()),
                ipAddress);
        return response(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SupplierResponse setActivation(
            Long id,
            boolean value,
            Long version,
            Long actorId,
            String ipAddress) {
        SupplierEntity supplier = locked(id);
        verifyVersion(supplier, version);
        supplier.setActive(value);
        SupplierEntity saved = save(supplier);
        audit.record(
                value ? "SUPPLIER_ACTIVATED" : "SUPPLIER_DEACTIVATED",
                actorId,
                "SUPPLIER",
                id,
                Map.of("active", value),
                ipAddress);
        return response(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SupplierItemResponse upsertItem(
            Long supplierId,
            SupplierItemRequest request,
            Long actorId,
            String ipAddress) {
        SupplierEntity supplier = locked(supplierId);
        InventoryItemEntity item = inventoryItems.findById(request.inventoryItemId())
                .orElseThrow(() -> InventoryManagementException.notFound("Inventory item"));
        if (request.active() && !item.isActive()) {
            throw InventoryManagementException.conflict("Inactive inventory items cannot be assigned");
        }
        SupplierInventoryItemEntity relationship = supplierItems
                .findBySupplierIdAndInventoryItemId(supplierId, item.getId())
                .orElse(null);
        if (relationship == null) {
            if (request.version() != null) {
                throw InventoryManagementException.stale();
            }
            relationship = new SupplierInventoryItemEntity(
                    supplier,
                    item,
                    InventoryService.optional(request.supplierItemCode()),
                    request.unitCost().setScale(4, java.math.RoundingMode.HALF_UP));
            if (!request.active()) {
                relationship.update(
                        InventoryService.optional(request.supplierItemCode()),
                        request.unitCost().setScale(4, java.math.RoundingMode.HALF_UP),
                        false);
            }
        } else {
            if (request.version() == null || relationship.getVersion() != request.version()) {
                throw InventoryManagementException.stale();
            }
            relationship.update(
                    InventoryService.optional(request.supplierItemCode()),
                    request.unitCost().setScale(4, java.math.RoundingMode.HALF_UP),
                    request.active());
        }
        SupplierInventoryItemEntity saved = supplierItems.saveAndFlush(relationship);
        audit.record(
                "SUPPLIER_UPDATED",
                actorId,
                "SUPPLIER",
                supplierId,
                Map.of("inventoryItemId", item.getId()),
                ipAddress);
        return itemResponse(saved);
    }

    private SupplierResponse response(SupplierEntity supplier) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getCode(),
                supplier.getName(),
                supplier.getContactName(),
                supplier.getEmail(),
                supplier.getPhone(),
                supplier.getNotes(),
                supplier.isActive(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt(),
                supplier.getVersion(),
                supplierItems.findBySupplierIdOrderByInventoryItemNameAscIdAsc(supplier.getId()).stream()
                        .map(this::itemResponse)
                        .toList());
    }

    private SupplierItemResponse itemResponse(SupplierInventoryItemEntity relationship) {
        InventoryItemEntity item = relationship.getInventoryItem();
        return new SupplierItemResponse(
                relationship.getId(),
                item.getId(),
                item.getCode(),
                item.getName(),
                item.getUnit(),
                relationship.getSupplierItemCode(),
                relationship.getUnitCost(),
                relationship.isActive(),
                relationship.getVersion());
    }

    private SupplierEntity find(Long id) {
        return suppliers.findById(id).orElseThrow(() -> InventoryManagementException.notFound("Supplier"));
    }

    private SupplierEntity locked(Long id) {
        return suppliers.findByIdForUpdate(id)
                .orElseThrow(() -> InventoryManagementException.notFound("Supplier"));
    }

    private SupplierEntity save(SupplierEntity supplier) {
        try {
            return suppliers.saveAndFlush(supplier);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw InventoryManagementException.stale();
        } catch (DataIntegrityViolationException exception) {
            throw InventoryManagementException.conflict("Supplier code must be unique");
        }
    }

    private void verifyVersion(SupplierEntity supplier, Long version) {
        if (version == null || supplier.getVersion() != version) {
            throw InventoryManagementException.stale();
        }
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
