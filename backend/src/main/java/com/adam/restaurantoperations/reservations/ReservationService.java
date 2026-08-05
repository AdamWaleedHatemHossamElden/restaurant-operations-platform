package com.adam.restaurantoperations.reservations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.adam.restaurantoperations.audit.ReservationAuditService;
import com.adam.restaurantoperations.auth.service.RequestMetadata;
import com.adam.restaurantoperations.reservations.dto.CreateReservationRequest;
import com.adam.restaurantoperations.reservations.dto.ReservationResponse;
import com.adam.restaurantoperations.reservations.dto.ReservationStatusRequest;
import com.adam.restaurantoperations.reservations.dto.TableAvailabilityResponse;
import com.adam.restaurantoperations.reservations.dto.UpdateReservationRequest;
import com.adam.restaurantoperations.tables.RestaurantTableEntity;
import com.adam.restaurantoperations.tables.RestaurantTableRepository;
import com.adam.restaurantoperations.tables.TableStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationService {

    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "reservationCode", "guestName", "partySize", "startAt", "durationMinutes", "status");

    private final ReservationRepository repository;
    private final RestaurantTableRepository tableRepository;
    private final ReservationCodeGenerator codeGenerator;
    private final ReservationAuditService auditService;

    public ReservationService(
            ReservationRepository repository,
            RestaurantTableRepository tableRepository,
            ReservationCodeGenerator codeGenerator,
            ReservationAuditService auditService) {
        this.repository = repository;
        this.tableRepository = tableRepository;
        this.codeGenerator = codeGenerator;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> list(
            Instant startFrom,
            Instant startTo,
            ReservationStatus status,
            Long tableId,
            Boolean assigned,
            String guestName,
            String reservationCode,
            String sortBy,
            Sort.Direction direction) {
        validateRange(startFrom, startTo);
        String sortField = SORTABLE_FIELDS.contains(sortBy) ? sortBy : "startAt";
        Sort sort = Sort.by(direction, sortField).and(Sort.by(Sort.Direction.ASC, "id"));
        return repository.findAll(
                        filters(startFrom, startTo, status, tableId, assigned, guestName, reservationCode),
                        sort)
                .stream()
                .map(ReservationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ReservationResponse get(Long id) {
        return ReservationResponse.from(find(id));
    }

    @Transactional
    public ReservationResponse create(
            CreateReservationRequest request,
            Long actorUserId,
            RequestMetadata metadata) {
        RestaurantTableEntity table = lockAndValidateAssignment(
                request.restaurantTableId(),
                request.partySize(),
                request.startAt(),
                request.durationMinutes(),
                null);
        String code = generateUniqueCode();
        var reservation = new ReservationEntity(
                code,
                normalizeRequired(request.guestName()),
                normalizeRequired(request.guestPhone()),
                normalizeOptionalEmail(request.guestEmail()),
                request.partySize(),
                request.startAt(),
                request.durationMinutes(),
                table,
                normalizeOptional(request.notes()));
        ReservationEntity saved = save(reservation);
        auditService.record("RESERVATION_CREATED", actorUserId, saved.getId(), tableId(table), metadata.ipAddress());
        if (table != null) {
            auditService.record(
                    "RESERVATION_TABLE_ASSIGNED",
                    actorUserId,
                    saved.getId(),
                    table.getId(),
                    metadata.ipAddress());
        }
        return ReservationResponse.from(saved);
    }

    @Transactional
    public ReservationResponse update(
            Long id,
            UpdateReservationRequest request,
            Long actorUserId,
            RequestMetadata metadata) {
        ReservationEntity reservation = find(id);
        verifyVersion(reservation, request.version());
        if (reservation.getStatus().isTerminal()) {
            throw ReservationManagementException.terminalReservation();
        }

        Long previousTableId = tableId(reservation.getRestaurantTable());
        RestaurantTableEntity table = lockAndValidateAssignment(
                request.restaurantTableId(),
                request.partySize(),
                request.startAt(),
                request.durationMinutes(),
                reservation.getId());
        reservation.update(
                normalizeRequired(request.guestName()),
                normalizeRequired(request.guestPhone()),
                normalizeOptionalEmail(request.guestEmail()),
                request.partySize(),
                request.startAt(),
                request.durationMinutes(),
                table,
                normalizeOptional(request.notes()));
        ReservationEntity saved = save(reservation);
        Long currentTableId = tableId(table);
        auditService.record(
                "RESERVATION_UPDATED", actorUserId, saved.getId(), currentTableId, metadata.ipAddress());
        if (!Objects.equals(previousTableId, currentTableId)) {
            String action = previousTableId == null && currentTableId != null
                    ? "RESERVATION_TABLE_ASSIGNED"
                    : "RESERVATION_TABLE_CHANGED";
            auditService.record(action, actorUserId, saved.getId(), currentTableId, metadata.ipAddress());
        }
        return ReservationResponse.from(saved);
    }

    @Transactional
    public ReservationResponse transition(
            Long id,
            ReservationStatusRequest request,
            Long actorUserId,
            RequestMetadata metadata) {
        ReservationEntity reservation = find(id);
        verifyVersion(reservation, request.version());
        if (!reservation.getStatus().canTransitionTo(request.status())) {
            throw ReservationManagementException.invalidTransition();
        }
        if (request.status().blocksAvailability() && reservation.getRestaurantTable() != null) {
            lockAndValidateAssignment(
                    reservation.getRestaurantTable().getId(),
                    reservation.getPartySize(),
                    reservation.getStartAt(),
                    reservation.getDurationMinutes(),
                    reservation.getId());
        }
        reservation.transitionTo(request.status());
        ReservationEntity saved = save(reservation);
        auditService.record(
                auditAction(request.status()),
                actorUserId,
                saved.getId(),
                tableId(saved.getRestaurantTable()),
                metadata.ipAddress());
        return ReservationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<TableAvailabilityResponse> availability(
            Instant startAt,
            int durationMinutes,
            int partySize,
            Long excludeReservationId) {
        Instant endAt = startAt.plus(durationMinutes, ChronoUnit.MINUTES);
        return tableRepository.findAll(Sort.by("capacity").ascending().and(Sort.by("tableNumber"))).stream()
                .filter(RestaurantTableEntity::isActive)
                .filter(table -> table.getStatus() == TableStatus.AVAILABLE)
                .filter(table -> table.getCapacity() >= partySize)
                .filter(table -> repository.countBlockingOverlaps(
                                table.getId(), startAt, endAt, excludeReservationId)
                        == 0)
                .map(TableAvailabilityResponse::from)
                .toList();
    }

    private RestaurantTableEntity lockAndValidateAssignment(
            Long tableId,
            int partySize,
            Instant startAt,
            int durationMinutes,
            Long excludeReservationId) {
        if (tableId == null) {
            return null;
        }
        try {
            RestaurantTableEntity table = tableRepository.findByIdForReservationUpdate(tableId)
                    .orElseThrow(ReservationManagementException::tableNotFound);
            if (!table.isActive()) {
                throw ReservationManagementException.tableUnavailable("Restaurant table is inactive");
            }
            if (table.getStatus() != TableStatus.AVAILABLE) {
                throw ReservationManagementException.tableUnavailable("Restaurant table is out of service");
            }
            if (table.getCapacity() < partySize) {
                throw ReservationManagementException.tableUnavailable("Restaurant table capacity is insufficient");
            }
            Instant endAt = startAt.plus(durationMinutes, ChronoUnit.MINUTES);
            if (!repository.findBlockingOverlapIdsForUpdate(
                            tableId, startAt, endAt, excludeReservationId)
                    .isEmpty()) {
                throw ReservationManagementException.tableUnavailable(
                        "Restaurant table is unavailable for the requested time");
            }
            return table;
        } catch (PessimisticLockingFailureException exception) {
            throw ReservationManagementException.contention();
        }
    }

    private Specification<ReservationEntity> filters(
            Instant startFrom,
            Instant startTo,
            ReservationStatus status,
            Long tableId,
            Boolean assigned,
            String guestName,
            String reservationCode) {
        return (root, query, criteriaBuilder) -> {
            Predicate predicate = criteriaBuilder.conjunction();
            if (startFrom != null) {
                predicate = criteriaBuilder.and(
                        predicate, criteriaBuilder.greaterThanOrEqualTo(root.get("startAt"), startFrom));
            }
            if (startTo != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.lessThan(root.get("startAt"), startTo));
            }
            if (status != null) {
                predicate = criteriaBuilder.and(predicate, criteriaBuilder.equal(root.get("status"), status));
            }
            if (tableId != null) {
                predicate = criteriaBuilder.and(
                        predicate, criteriaBuilder.equal(root.get("restaurantTable").get("id"), tableId));
            }
            if (assigned != null) {
                Predicate assignmentPredicate = assigned
                        ? criteriaBuilder.isNotNull(root.get("restaurantTable"))
                        : criteriaBuilder.isNull(root.get("restaurantTable"));
                predicate = criteriaBuilder.and(predicate, assignmentPredicate);
            }
            if (hasText(guestName)) {
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("guestName")),
                                "%" + guestName.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (hasText(reservationCode)) {
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.like(
                                criteriaBuilder.lower(root.get("reservationCode")),
                                "%" + reservationCode.trim().toLowerCase(Locale.ROOT) + "%"));
            }
            return predicate;
        };
    }

    private ReservationEntity find(Long id) {
        return repository.findById(id).orElseThrow(ReservationManagementException::notFound);
    }

    private ReservationEntity save(ReservationEntity reservation) {
        try {
            return repository.saveAndFlush(reservation);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw ReservationManagementException.versionConflict();
        } catch (DataIntegrityViolationException exception) {
            throw ReservationManagementException.codeConflict();
        }
    }

    private void verifyVersion(ReservationEntity reservation, Long version) {
        if (reservation.getVersion() != version) {
            throw ReservationManagementException.versionConflict();
        }
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = codeGenerator.generate();
            if (!repository.existsByReservationCode(candidate)) {
                return candidate;
            }
        }
        throw ReservationManagementException.codeConflict();
    }

    private void validateRange(Instant startFrom, Instant startTo) {
        if (startFrom != null && startTo != null && !startFrom.isBefore(startTo)) {
            throw ReservationManagementException.invalidRange();
        }
    }

    private String auditAction(ReservationStatus status) {
        return switch (status) {
            case CONFIRMED -> "RESERVATION_CONFIRMED";
            case SEATED -> "RESERVATION_SEATED";
            case COMPLETED -> "RESERVATION_COMPLETED";
            case CANCELLED -> "RESERVATION_CANCELLED";
            case NO_SHOW -> "RESERVATION_NO_SHOW";
            case PENDING -> throw ReservationManagementException.invalidTransition();
        };
    }

    private String normalizeRequired(String value) {
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String normalizeOptionalEmail(String value) {
        return hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    private Long tableId(RestaurantTableEntity table) {
        return table == null ? null : table.getId();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
