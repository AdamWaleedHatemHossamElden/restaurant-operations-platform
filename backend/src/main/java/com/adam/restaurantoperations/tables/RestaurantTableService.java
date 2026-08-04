package com.adam.restaurantoperations.tables;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.adam.restaurantoperations.audit.TableAuditService;
import com.adam.restaurantoperations.auth.service.RequestMetadata;
import com.adam.restaurantoperations.tables.dto.CreateTableRequest;
import com.adam.restaurantoperations.tables.dto.TableActivationRequest;
import com.adam.restaurantoperations.tables.dto.TableResponse;
import com.adam.restaurantoperations.tables.dto.UpdateTableRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestaurantTableService {

    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "tableNumber", "displayName", "capacity", "section", "status", "active");

    private final RestaurantTableRepository repository;
    private final TableAuditService auditService;

    public RestaurantTableService(
            RestaurantTableRepository repository,
            TableAuditService auditService) {
        this.repository = repository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<TableResponse> list(
            Boolean active,
            String section,
            TableStatus status,
            String tableNumber,
            String sortBy,
            Sort.Direction direction) {
        String sortField = SORTABLE_FIELDS.contains(sortBy) ? sortBy : "tableNumber";
        Sort sort = Sort.by(direction, sortField).and(Sort.by(Sort.Direction.ASC, "id"));
        return repository.findAll(filters(active, section, status, tableNumber), sort).stream()
                .map(TableResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TableResponse get(Long id) {
        return TableResponse.from(find(id));
    }

    @Transactional
    public TableResponse create(
            CreateTableRequest request,
            Long actorUserId,
            RequestMetadata metadata) {
        String tableNumber = normalizeTableNumber(request.tableNumber());
        if (repository.existsByTableNumberIgnoreCase(tableNumber)) {
            throw TableManagementException.duplicateTableNumber();
        }

        var table = new RestaurantTableEntity(
                tableNumber,
                request.displayName().trim(),
                request.capacity(),
                request.section().trim(),
                request.status());
        RestaurantTableEntity saved = save(table);
        auditService.record("TABLE_CREATED", actorUserId, saved.getId(), metadata.ipAddress());
        return TableResponse.from(saved);
    }

    @Transactional
    public TableResponse update(
            Long id,
            UpdateTableRequest request,
            Long actorUserId,
            RequestMetadata metadata) {
        RestaurantTableEntity table = find(id);
        verifyVersion(table, request.version());
        String tableNumber = normalizeTableNumber(request.tableNumber());
        if (repository.existsByTableNumberIgnoreCaseAndIdNot(tableNumber, id)) {
            throw TableManagementException.duplicateTableNumber();
        }

        table.update(
                tableNumber,
                request.displayName().trim(),
                request.capacity(),
                request.section().trim(),
                request.status());
        RestaurantTableEntity saved = save(table);
        auditService.record("TABLE_UPDATED", actorUserId, saved.getId(), metadata.ipAddress());
        return TableResponse.from(saved);
    }

    @Transactional
    public TableResponse setActivation(
            Long id,
            TableActivationRequest request,
            Long actorUserId,
            RequestMetadata metadata) {
        RestaurantTableEntity table = find(id);
        verifyVersion(table, request.version());
        if (table.isActive() == request.active()) {
            return TableResponse.from(table);
        }

        table.setActive(request.active());
        RestaurantTableEntity saved = save(table);
        String action = request.active() ? "TABLE_REACTIVATED" : "TABLE_DEACTIVATED";
        auditService.record(action, actorUserId, saved.getId(), metadata.ipAddress());
        return TableResponse.from(saved);
    }

    private Specification<RestaurantTableEntity> filters(
            Boolean active,
            String section,
            TableStatus status,
            String tableNumber) {
        return (root, query, criteriaBuilder) -> {
            Predicate predicate = criteriaBuilder.conjunction();
            if (active != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("active"), active));
            }
            if (hasText(section)) {
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.equal(
                                criteriaBuilder.lower(root.get("section")),
                                section.trim().toLowerCase(Locale.ROOT)));
            }
            if (status != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("status"), status));
            }
            if (hasText(tableNumber)) {
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("tableNumber")),
                                "%" + tableNumber.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            return predicate;
        };
    }

    private RestaurantTableEntity find(Long id) {
        return repository.findById(id).orElseThrow(TableManagementException::notFound);
    }

    private RestaurantTableEntity save(RestaurantTableEntity table) {
        try {
            return repository.saveAndFlush(table);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw TableManagementException.versionConflict();
        } catch (DataIntegrityViolationException exception) {
            throw TableManagementException.duplicateTableNumber();
        }
    }

    private void verifyVersion(RestaurantTableEntity table, Long version) {
        if (table.getVersion() != version) {
            throw TableManagementException.versionConflict();
        }
    }

    private String normalizeTableNumber(String tableNumber) {
        return tableNumber.trim().toUpperCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
