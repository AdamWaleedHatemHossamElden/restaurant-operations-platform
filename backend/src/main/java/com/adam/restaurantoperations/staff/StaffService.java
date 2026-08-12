package com.adam.restaurantoperations.staff;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.adam.restaurantoperations.audit.StaffAuditService;
import com.adam.restaurantoperations.staff.StaffDtos.ActivationRequest;
import com.adam.restaurantoperations.staff.StaffDtos.AvailabilityRequest;
import com.adam.restaurantoperations.staff.StaffDtos.AvailabilityResponse;
import com.adam.restaurantoperations.staff.StaffDtos.EmployeeRequest;
import com.adam.restaurantoperations.staff.StaffDtos.EmployeeResponse;
import com.adam.restaurantoperations.staff.StaffDtos.ShiftRequest;
import com.adam.restaurantoperations.staff.StaffDtos.ShiftResponse;
import com.adam.restaurantoperations.staff.StaffDtos.ShiftStatusRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffService {
    private static final Set<String> EMPLOYEE_SORTS = Set.of(
            "employeeCode", "firstName", "lastName", "defaultOperationalRole", "createdAt");
    private static final Set<String> SHIFT_SORTS = Set.of(
            "startAt", "endAt", "employee", "operationalRole", "status", "createdAt");

    private final EmployeeRepository employees;
    private final EmployeeAvailabilityRepository availability;
    private final ShiftRepository shifts;
    private final StaffAuditService audit;

    public StaffService(
            EmployeeRepository employees,
            EmployeeAvailabilityRepository availability,
            ShiftRepository shifts,
            StaffAuditService audit) {
        this.employees = employees;
        this.availability = availability;
        this.shifts = shifts;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> listEmployees(
            String search,
            OperationalRole role,
            Boolean active,
            String sortBy,
            Sort.Direction direction) {
        String field = EMPLOYEE_SORTS.contains(sortBy) ? sortBy : "lastName";
        Sort sort = Sort.by(direction, field)
                .and(Sort.by(Sort.Direction.ASC, "firstName"))
                .and(Sort.by(Sort.Direction.ASC, "id"));
        return employees.findAll(employeeFilters(search, role, active), sort).stream()
                .map(EmployeeResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getEmployee(Long id) {
        return EmployeeResponse.from(findEmployee(id));
    }

    @Transactional
    public EmployeeResponse createEmployee(EmployeeRequest request, Long actorId, String ipAddress) {
        EmployeeEntity employee = new EmployeeEntity(
                code(request.employeeCode()),
                required(request.firstName()),
                required(request.lastName()),
                optional(request.email()),
                optional(request.phone()),
                request.defaultOperationalRole(),
                request.employmentStartDate());
        EmployeeEntity saved = saveEmployee(employee);
        audit.record(
                "EMPLOYEE_CREATED",
                actorId,
                "EMPLOYEE",
                saved.getId(),
                Map.of("employeeCode", saved.getEmployeeCode()),
                ipAddress);
        return EmployeeResponse.from(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EmployeeResponse updateEmployee(
            Long id,
            EmployeeRequest request,
            Long actorId,
            String ipAddress) {
        EmployeeEntity employee = lockEmployee(id);
        verifyVersion(employee.getVersion(), request.version(), "Employee");
        employee.update(
                code(request.employeeCode()),
                required(request.firstName()),
                required(request.lastName()),
                optional(request.email()),
                optional(request.phone()),
                request.defaultOperationalRole(),
                request.employmentStartDate());
        EmployeeEntity saved = saveEmployee(employee);
        audit.record(
                "EMPLOYEE_UPDATED",
                actorId,
                "EMPLOYEE",
                saved.getId(),
                Map.of("employeeCode", saved.getEmployeeCode()),
                ipAddress);
        return EmployeeResponse.from(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EmployeeResponse setEmployeeActivation(
            Long id,
            ActivationRequest request,
            Long actorId,
            String ipAddress) {
        EmployeeEntity employee = lockEmployee(id);
        verifyVersion(employee.getVersion(), request.version(), "Employee");
        if (!request.active() && employee.isActive()
                && shifts.countFutureScheduled(employee.getId(), Instant.now()) > 0) {
            throw StaffManagementException.conflict(
                    "Cancel future scheduled shifts before deactivating this employee");
        }
        employee.setActive(request.active());
        EmployeeEntity saved = saveEmployee(employee);
        audit.record(
                request.active() ? "EMPLOYEE_ACTIVATED" : "EMPLOYEE_DEACTIVATED",
                actorId,
                "EMPLOYEE",
                saved.getId(),
                Map.of("employeeCode", saved.getEmployeeCode()),
                ipAddress);
        return EmployeeResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> listAvailability(Long employeeId, Instant startAt, Instant endAt) {
        validateRange(startAt, endAt);
        findEmployee(employeeId);
        return availability.findInRange(employeeId, startAt, endAt).stream()
                .map(AvailabilityResponse::from)
                .toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AvailabilityResponse createAvailability(
            Long employeeId,
            AvailabilityRequest request,
            Long actorId,
            String ipAddress) {
        validateRange(request.startAt(), request.endAt());
        EmployeeEntity employee = lockActiveEmployee(employeeId);
        ensureNoAvailabilityOverlap(employeeId, request.startAt(), request.endAt(), null);
        EmployeeAvailabilityEntity saved = saveAvailability(new EmployeeAvailabilityEntity(
                employee,
                request.startAt(),
                request.endAt(),
                optional(request.notes())));
        audit.record(
                "EMPLOYEE_AVAILABILITY_CREATED",
                actorId,
                "EMPLOYEE_AVAILABILITY",
                saved.getId(),
                Map.of("employeeId", employeeId),
                ipAddress);
        return AvailabilityResponse.from(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AvailabilityResponse updateAvailability(
            Long employeeId,
            Long id,
            AvailabilityRequest request,
            Long actorId,
            String ipAddress) {
        validateRange(request.startAt(), request.endAt());
        lockActiveEmployee(employeeId);
        EmployeeAvailabilityEntity existing = availability.findByEmployeeAndIdForUpdate(employeeId, id)
                .orElseThrow(StaffManagementException::availabilityNotFound);
        verifyVersion(existing.getVersion(), request.version(), "Availability");
        ensureNoAvailabilityOverlap(employeeId, request.startAt(), request.endAt(), existing.getId());
        existing.update(request.startAt(), request.endAt(), optional(request.notes()));
        EmployeeAvailabilityEntity saved = saveAvailability(existing);
        audit.record(
                "EMPLOYEE_AVAILABILITY_UPDATED",
                actorId,
                "EMPLOYEE_AVAILABILITY",
                saved.getId(),
                Map.of("employeeId", employeeId),
                ipAddress);
        return AvailabilityResponse.from(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void removeAvailability(
            Long employeeId,
            Long id,
            long version,
            Long actorId,
            String ipAddress) {
        lockEmployee(employeeId);
        EmployeeAvailabilityEntity existing = availability.findByEmployeeAndIdForUpdate(employeeId, id)
                .orElseThrow(StaffManagementException::availabilityNotFound);
        verifyVersion(existing.getVersion(), version, "Availability");
        availability.delete(existing);
        availability.flush();
        audit.record(
                "EMPLOYEE_AVAILABILITY_REMOVED",
                actorId,
                "EMPLOYEE_AVAILABILITY",
                id,
                Map.of("employeeId", employeeId),
                ipAddress);
    }

    @Transactional(readOnly = true)
    public List<ShiftResponse> listShifts(
            Long employeeId,
            OperationalRole role,
            ShiftStatus status,
            Instant startFrom,
            Instant startTo,
            String search,
            String sortBy,
            Sort.Direction direction) {
        if (startFrom != null && startTo != null) {
            validateRange(startFrom, startTo);
        }
        String field = SHIFT_SORTS.contains(sortBy) ? sortBy : "startAt";
        Sort sort = "employee".equals(field)
                ? Sort.by(direction, "employee.lastName")
                        .and(Sort.by(direction, "employee.firstName"))
                        .and(Sort.by(Sort.Direction.ASC, "id"))
                : Sort.by(direction, field).and(Sort.by(Sort.Direction.ASC, "id"));
        return shifts.findAll(shiftFilters(employeeId, role, status, startFrom, startTo, search), sort).stream()
                .map(ShiftResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShiftResponse getShift(Long id) {
        return ShiftResponse.from(shifts.findById(id).orElseThrow(StaffManagementException::shiftNotFound));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShiftResponse createShift(ShiftRequest request, Long actorId, String ipAddress) {
        validateRange(request.startAt(), request.endAt());
        EmployeeEntity employee = lockActiveEmployee(request.employeeId());
        validateSchedule(employee, request.startAt(), request.endAt(), null);
        ShiftEntity saved = saveShift(new ShiftEntity(
                employee,
                request.operationalRole(),
                request.startAt(),
                request.endAt(),
                optional(request.notes())));
        auditShift("SHIFT_CREATED", saved, actorId, ipAddress);
        return ShiftResponse.from(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShiftResponse updateShift(Long id, ShiftRequest request, Long actorId, String ipAddress) {
        validateRange(request.startAt(), request.endAt());
        Long originalEmployeeId = shifts.findEmployeeId(id)
                .orElseThrow(StaffManagementException::shiftNotFound);
        List<EmployeeEntity> lockedEmployees = lockEmployees(originalEmployeeId, request.employeeId());
        ShiftEntity shift = shifts.findById(id).orElseThrow(StaffManagementException::shiftNotFound);
        if (!lockedEmployees.stream().map(EmployeeEntity::getId).toList()
                .contains(shift.getEmployee().getId())) {
            throw StaffManagementException.contention();
        }
        verifyVersion(shift.getVersion(), request.version(), "Shift");
        if (shift.getStatus().isTerminal()) {
            throw StaffManagementException.conflict("Terminal shifts cannot be edited");
        }
        EmployeeEntity employee = lockedEmployees.stream()
                .filter(value -> value.getId().equals(request.employeeId()))
                .findFirst()
                .orElseThrow(StaffManagementException::employeeNotFound);
        ensureActive(employee);
        validateSchedule(employee, request.startAt(), request.endAt(), shift.getId());
        shift = shifts.findByIdForUpdate(id).orElseThrow(StaffManagementException::shiftNotFound);
        shift.update(
                employee,
                request.operationalRole(),
                request.startAt(),
                request.endAt(),
                optional(request.notes()));
        ShiftEntity saved = saveShift(shift);
        auditShift("SHIFT_UPDATED", saved, actorId, ipAddress);
        return ShiftResponse.from(saved);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ShiftResponse transitionShift(
            Long id,
            ShiftStatusRequest request,
            Long actorId,
            String ipAddress) {
        Long employeeId = shifts.findEmployeeId(id).orElseThrow(StaffManagementException::shiftNotFound);
        lockEmployee(employeeId);
        ShiftEntity shift = shifts.findByIdForUpdate(id).orElseThrow(StaffManagementException::shiftNotFound);
        if (!shift.getEmployee().getId().equals(employeeId)) {
            throw StaffManagementException.contention();
        }
        verifyVersion(shift.getVersion(), request.version(), "Shift");
        if (shift.getStatus() != ShiftStatus.SCHEDULED
                || request.status() == ShiftStatus.SCHEDULED) {
            throw StaffManagementException.conflict("Shift status transition is not allowed");
        }
        shift.transitionTo(request.status());
        ShiftEntity saved = saveShift(shift);
        auditShift(request.status() == ShiftStatus.COMPLETED ? "SHIFT_COMPLETED" : "SHIFT_CANCELLED",
                saved,
                actorId,
                ipAddress);
        return ShiftResponse.from(saved);
    }

    private void validateSchedule(EmployeeEntity employee, Instant startAt, Instant endAt, Long excludeId) {
        if (availability.findCoveringIdsForUpdate(employee.getId(), startAt, endAt).isEmpty()) {
            throw StaffManagementException.conflict(
                    "Shift must be fully contained within employee availability");
        }
        if (!shifts.findBlockingOverlapIdsForUpdate(employee.getId(), startAt, endAt, excludeId).isEmpty()) {
            throw StaffManagementException.conflict("Employee already has an overlapping shift");
        }
    }

    private void ensureNoAvailabilityOverlap(Long employeeId, Instant startAt, Instant endAt, Long excludeId) {
        if (!availability.findOverlapIdsForUpdate(employeeId, startAt, endAt, excludeId).isEmpty()) {
            throw StaffManagementException.conflict("Employee availability windows cannot overlap");
        }
    }

    private List<EmployeeEntity> lockEmployees(Long firstId, Long secondId) {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(firstId);
        ids.add(secondId);
        List<EmployeeEntity> result = new ArrayList<>();
        ids.stream().sorted().forEach(id -> result.add(lockEmployee(id)));
        return result;
    }

    private EmployeeEntity lockActiveEmployee(Long id) {
        EmployeeEntity employee = lockEmployee(id);
        ensureActive(employee);
        return employee;
    }

    private EmployeeEntity lockEmployee(Long id) {
        try {
            return employees.findByIdForUpdate(id).orElseThrow(StaffManagementException::employeeNotFound);
        } catch (PessimisticLockingFailureException exception) {
            throw StaffManagementException.contention();
        }
    }

    private EmployeeEntity findEmployee(Long id) {
        return employees.findById(id).orElseThrow(StaffManagementException::employeeNotFound);
    }

    private void ensureActive(EmployeeEntity employee) {
        if (!employee.isActive()) {
            throw StaffManagementException.conflict("Inactive employees cannot receive scheduling changes");
        }
    }

    private EmployeeEntity saveEmployee(EmployeeEntity employee) {
        try {
            return employees.saveAndFlush(employee);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw StaffManagementException.conflict("Employee changed; reload and retry");
        } catch (DataIntegrityViolationException exception) {
            throw StaffManagementException.conflict("Employee code is already in use");
        }
    }

    private EmployeeAvailabilityEntity saveAvailability(EmployeeAvailabilityEntity value) {
        try {
            return availability.saveAndFlush(value);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw StaffManagementException.conflict("Availability changed; reload and retry");
        } catch (DataIntegrityViolationException exception) {
            throw StaffManagementException.contention();
        }
    }

    private ShiftEntity saveShift(ShiftEntity shift) {
        try {
            return shifts.saveAndFlush(shift);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw StaffManagementException.conflict("Shift changed; reload and retry");
        } catch (DataIntegrityViolationException exception) {
            throw StaffManagementException.contention();
        }
    }

    private void verifyVersion(long actual, Long requested, String resource) {
        if (requested == null || actual != requested) {
            throw StaffManagementException.conflict(resource + " changed; reload and retry");
        }
    }

    private void validateRange(Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw StaffManagementException.invalidRange();
        }
    }

    private Specification<EmployeeEntity> employeeFilters(
            String search,
            OperationalRole role,
            Boolean active) {
        return (root, query, builder) -> {
            Predicate predicate = builder.conjunction();
            if (hasText(search)) {
                String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicate = builder.and(predicate, builder.or(
                        builder.like(builder.lower(root.get("employeeCode")), term),
                        builder.like(builder.lower(root.get("firstName")), term),
                        builder.like(builder.lower(root.get("lastName")), term)));
            }
            if (role != null) {
                predicate = builder.and(predicate, builder.equal(root.get("defaultOperationalRole"), role));
            }
            if (active != null) {
                predicate = builder.and(predicate, builder.equal(root.get("active"), active));
            }
            return predicate;
        };
    }

    private Specification<ShiftEntity> shiftFilters(
            Long employeeId,
            OperationalRole role,
            ShiftStatus status,
            Instant startFrom,
            Instant startTo,
            String search) {
        return (root, query, builder) -> {
            Predicate predicate = builder.conjunction();
            if (employeeId != null) {
                predicate = builder.and(predicate, builder.equal(root.get("employee").get("id"), employeeId));
            }
            if (role != null) {
                predicate = builder.and(predicate, builder.equal(root.get("operationalRole"), role));
            }
            if (status != null) {
                predicate = builder.and(predicate, builder.equal(root.get("status"), status));
            }
            if (startFrom != null) {
                predicate = builder.and(predicate, builder.greaterThan(root.get("endAt"), startFrom));
            }
            if (startTo != null) {
                predicate = builder.and(predicate, builder.lessThan(root.get("startAt"), startTo));
            }
            if (hasText(search)) {
                String term = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicate = builder.and(predicate, builder.or(
                        builder.like(builder.lower(root.get("employee").get("employeeCode")), term),
                        builder.like(builder.lower(root.get("employee").get("firstName")), term),
                        builder.like(builder.lower(root.get("employee").get("lastName")), term)));
            }
            return predicate;
        };
    }

    private void auditShift(String action, ShiftEntity shift, Long actorId, String ipAddress) {
        audit.record(
                action,
                actorId,
                "SHIFT",
                shift.getId(),
                Map.of(
                        "employeeId", shift.getEmployee().getId(),
                        "operationalRole", shift.getOperationalRole().name(),
                        "status", shift.getStatus().name()),
                ipAddress);
    }

    private String code(String value) {
        return required(value).toUpperCase(Locale.ROOT);
    }

    private String required(String value) {
        return value.trim();
    }

    private String optional(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
