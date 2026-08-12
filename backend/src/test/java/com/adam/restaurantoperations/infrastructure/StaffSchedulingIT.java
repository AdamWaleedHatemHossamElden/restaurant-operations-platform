package com.adam.restaurantoperations.infrastructure;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.adam.restaurantoperations.staff.OperationalRole;
import com.adam.restaurantoperations.staff.ShiftStatus;
import com.adam.restaurantoperations.staff.StaffDtos.ActivationRequest;
import com.adam.restaurantoperations.staff.StaffDtos.AvailabilityRequest;
import com.adam.restaurantoperations.staff.StaffDtos.EmployeeRequest;
import com.adam.restaurantoperations.staff.StaffDtos.EmployeeResponse;
import com.adam.restaurantoperations.staff.StaffDtos.ShiftRequest;
import com.adam.restaurantoperations.staff.StaffDtos.ShiftResponse;
import com.adam.restaurantoperations.staff.StaffDtos.ShiftStatusRequest;
import com.adam.restaurantoperations.staff.StaffManagementException;
import com.adam.restaurantoperations.staff.StaffService;
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
class StaffSchedulingIT {
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
    @Autowired private StaffService staffService;

    private Long actorId;

    @BeforeEach
    void cleanAndSeedActor() {
        jdbcTemplate.update("DELETE FROM shifts");
        jdbcTemplate.update("DELETE FROM employee_availability");
        jdbcTemplate.update("DELETE FROM employees");
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM user_roles");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("DELETE FROM roles");
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, display_name, enabled) VALUES (?, ?, ?, TRUE)",
                "staff-admin@example.com",
                "integration-test-password-hash",
                "Staff Admin");
        actorId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'staff-admin@example.com'",
                Long.class);
    }

    @Test
    void employeeAvailabilityAndShiftPoliciesRemainCoherent() {
        EmployeeResponse maria = employee(" emp001 ", " Maria ", " Rossi ", OperationalRole.WAITER);
        assertThat(maria.employeeCode()).isEqualTo("EMP001");
        assertThatThrownBy(() -> employee("emp001", "Other", "Person", OperationalRole.HOST))
                .hasMessageContaining("already in use");

        Instant nine = Instant.parse("2030-06-03T09:00:00Z");
        Instant seventeen = Instant.parse("2030-06-03T17:00:00Z");
        var window = staffService.createAvailability(
                maria.id(), availability(nine, seventeen, null), actorId, "127.0.0.1");
        assertThat(staffService.listAvailability(
                        maria.id(), Instant.parse("2030-06-03T00:00:00Z"), Instant.parse("2030-06-04T00:00:00Z")))
                .hasSize(1);
        assertThatThrownBy(() -> staffService.createAvailability(
                        maria.id(),
                        availability(Instant.parse("2030-06-03T16:00:00Z"),
                                Instant.parse("2030-06-03T18:00:00Z"), null),
                        actorId,
                        "127.0.0.1"))
                .hasMessageContaining("cannot overlap");
        staffService.createAvailability(
                maria.id(),
                availability(seventeen, Instant.parse("2030-06-03T18:00:00Z"), null),
                actorId,
                "127.0.0.1");

        ShiftResponse first = staffService.createShift(
                shift(maria.id(), OperationalRole.HOST,
                        Instant.parse("2030-06-03T10:00:00Z"),
                        Instant.parse("2030-06-03T16:00:00Z"), null),
                actorId,
                "127.0.0.1");
        assertThat(first.operationalRole()).isEqualTo(OperationalRole.HOST);
        assertThat(maria.defaultOperationalRole()).isEqualTo(OperationalRole.WAITER);
        assertThatThrownBy(() -> staffService.createShift(
                        shift(maria.id(), OperationalRole.WAITER,
                                Instant.parse("2030-06-03T08:00:00Z"),
                                Instant.parse("2030-06-03T12:00:00Z"), null),
                        actorId,
                        "127.0.0.1"))
                .hasMessageContaining("fully contained");
        assertThatThrownBy(() -> staffService.createShift(
                        shift(maria.id(), OperationalRole.WAITER,
                                Instant.parse("2030-06-03T15:00:00Z"), seventeen, null),
                        actorId,
                        "127.0.0.1"))
                .hasMessageContaining("overlapping shift");
        ShiftResponse adjacent = staffService.createShift(
                shift(maria.id(), OperationalRole.WAITER,
                        Instant.parse("2030-06-03T16:00:00Z"), seventeen, null),
                actorId,
                "127.0.0.1");

        assertThatThrownBy(() -> staffService.setEmployeeActivation(
                        maria.id(), new ActivationRequest(false, maria.version()), actorId, "127.0.0.1"))
                .hasMessageContaining("Cancel future scheduled shifts");
        ShiftResponse completed = staffService.transitionShift(
                first.id(), new ShiftStatusRequest(ShiftStatus.COMPLETED, first.version()), actorId, "127.0.0.1");
        ShiftResponse cancelled = staffService.transitionShift(
                adjacent.id(), new ShiftStatusRequest(ShiftStatus.CANCELLED, adjacent.version()), actorId, "127.0.0.1");
        assertThatThrownBy(() -> staffService.updateShift(
                        completed.id(),
                        shift(maria.id(), OperationalRole.HOST, completed.startAt(), completed.endAt(),
                                completed.version()),
                        actorId,
                        "127.0.0.1"))
                .hasMessageContaining("Terminal shifts");
        assertThat(cancelled.status()).isEqualTo(ShiftStatus.CANCELLED);
        EmployeeResponse latest = staffService.getEmployee(maria.id());
        EmployeeResponse inactive = staffService.setEmployeeActivation(
                maria.id(), new ActivationRequest(false, latest.version()), actorId, "127.0.0.1");
        assertThat(inactive.active()).isFalse();
        assertThatThrownBy(() -> staffService.createShift(
                        shift(maria.id(), OperationalRole.WAITER,
                                Instant.parse("2030-06-03T17:00:00Z"),
                                Instant.parse("2030-06-03T18:00:00Z"), null),
                        actorId,
                        "127.0.0.1"))
                .hasMessageContaining("Inactive employees");
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE action = 'SHIFT_CREATED'"))
                .isEqualTo(2);
        assertThat(window.startAt()).isEqualTo(nine);
    }

    @Test
    void staleAndFailedOperationsDoNotPersistSuccessAudits() {
        EmployeeResponse nikos = employee("EMP002", "Nikos", "Pappas", OperationalRole.KITCHEN);
        Instant start = Instant.parse("2030-06-04T14:00:00Z");
        Instant end = Instant.parse("2030-06-04T22:00:00Z");
        var window = staffService.createAvailability(
                nikos.id(), availability(start, end, "Service"), actorId, "127.0.0.1");
        assertThatThrownBy(() -> staffService.updateAvailability(
                        nikos.id(), window.id(), availability(start, end, window.version() + 1),
                        actorId, "127.0.0.1"))
                .hasMessageContaining("Availability changed");
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE action = 'EMPLOYEE_AVAILABILITY_UPDATED'"))
                .isZero();

        ShiftResponse shift = staffService.createShift(
                shift(nikos.id(), OperationalRole.KITCHEN,
                        Instant.parse("2030-06-04T15:00:00Z"),
                        Instant.parse("2030-06-04T21:00:00Z"), null),
                actorId,
                "127.0.0.1");
        assertThatThrownBy(() -> staffService.transitionShift(
                        shift.id(), new ShiftStatusRequest(ShiftStatus.SCHEDULED, shift.version()),
                        actorId, "127.0.0.1"))
                .hasMessageContaining("transition is not allowed");
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE action = 'SHIFT_COMPLETED'"))
                .isZero();
    }

    @Test
    void simultaneousOverlappingShiftRequestsProduceOneSuccessAndOneSafeConflict() throws Exception {
        EmployeeResponse employee = employee("EMP003", "Elena", "Costa", OperationalRole.HOST);
        staffService.createAvailability(
                employee.id(),
                availability(Instant.parse("2030-06-05T09:00:00Z"),
                        Instant.parse("2030-06-05T18:00:00Z"), null),
                actorId,
                "127.0.0.1");
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> concurrentShift(
                    barrier,
                    employee.id(),
                    "2030-06-05T10:00:00Z",
                    "2030-06-05T14:00:00Z"));
            Future<Integer> second = executor.submit(() -> concurrentShift(
                    barrier,
                    employee.id(),
                    "2030-06-05T12:00:00Z",
                    "2030-06-05T16:00:00Z"));
            List<Integer> statuses = List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(count("SELECT COUNT(*) FROM shifts WHERE employee_id = ?", employee.id()))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE action = 'SHIFT_CREATED'"))
                .isEqualTo(1);
    }

    @Test
    void simultaneousOverlappingAvailabilityWritesAreSerializedPerEmployee() throws Exception {
        EmployeeResponse employee = employee("EMP004", "Theo", "Marin", OperationalRole.CASHIER);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> concurrentAvailability(
                    barrier,
                    employee.id(),
                    Instant.parse("2030-06-06T09:00:00Z"),
                    Instant.parse("2030-06-06T14:00:00Z")));
            Future<Boolean> second = executor.submit(() -> concurrentAvailability(
                    barrier,
                    employee.id(),
                    Instant.parse("2030-06-06T12:00:00Z"),
                    Instant.parse("2030-06-06T17:00:00Z")));
            assertThat(List.of(
                            first.get(20, TimeUnit.SECONDS),
                            second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(count(
                        "SELECT COUNT(*) FROM employee_availability WHERE employee_id = ?",
                        employee.id()))
                .isEqualTo(1);
        assertThat(count(
                        "SELECT COUNT(*) FROM audit_logs WHERE action = 'EMPLOYEE_AVAILABILITY_CREATED'"))
                .isEqualTo(1);
    }

    private int concurrentShift(CyclicBarrier barrier, Long employeeId, String start, String end)
            throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        return mockMvc.perform(post("/api/v1/staff/shifts")
                        .with(jwt()
                                .jwt(token -> token.subject(actorId.toString()).claim("roles", List.of("ADMIN")))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeId":%d,"operationalRole":"HOST","startAt":"%s",
                                 "endAt":"%s","notes":"Concurrent test"}
                                """.formatted(employeeId, start, end)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private boolean concurrentAvailability(
            CyclicBarrier barrier,
            Long employeeId,
            Instant start,
            Instant end) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try {
            staffService.createAvailability(
                    employeeId,
                    new AvailabilityRequest(start, end, null, null),
                    actorId,
                    "127.0.0.1");
            return true;
        } catch (StaffManagementException exception) {
            assertThat(exception.getStatus().value()).isEqualTo(409);
            return false;
        }
    }

    private EmployeeResponse employee(String code, String first, String last, OperationalRole role) {
        return staffService.createEmployee(
                new EmployeeRequest(code, first, last, null, null, role, LocalDate.of(2026, 1, 1), null),
                actorId,
                "127.0.0.1");
    }

    private AvailabilityRequest availability(Instant start, Instant end, Object notesOrVersion) {
        if (notesOrVersion instanceof Long version) {
            return new AvailabilityRequest(start, end, null, version);
        }
        return new AvailabilityRequest(start, end, (String) notesOrVersion, null);
    }

    private ShiftRequest shift(
            Long employeeId,
            OperationalRole role,
            Instant start,
            Instant end,
            Long version) {
        return new ShiftRequest(employeeId, role, start, end, null, version);
    }

    private int count(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, Integer.class, arguments);
    }
}
