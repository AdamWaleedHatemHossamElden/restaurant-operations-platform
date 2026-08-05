package com.adam.restaurantoperations.infrastructure;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ReservationManagementIT {

    private static final Instant START = Instant.parse("2030-04-12T18:00:00Z");

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long actorUserId;
    private Long largeTableId;
    private Long secondTableId;
    private Long smallTableId;
    private Long inactiveTableId;
    private Long outOfServiceTableId;

    @BeforeEach
    void cleanAndSeed() {
        jdbcTemplate.update("DELETE FROM reservations");
        jdbcTemplate.update("DELETE FROM restaurant_tables");
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM user_roles");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("DELETE FROM roles");
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, display_name, enabled) VALUES (?, ?, ?, TRUE)",
                "reservation-admin@example.com",
                "integration-test-password-hash",
                "Reservation Admin");
        actorUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'reservation-admin@example.com'",
                Long.class);
        largeTableId = insertTable("R-01", 6, true, "AVAILABLE");
        secondTableId = insertTable("R-02", 8, true, "AVAILABLE");
        smallTableId = insertTable("R-03", 2, true, "AVAILABLE");
        inactiveTableId = insertTable("R-04", 8, false, "AVAILABLE");
        outOfServiceTableId = insertTable("R-05", 8, true, "OUT_OF_SERVICE");
    }

    @Test
    void lifecycleValidationAvailabilityFilteringAndAuditRemainConsistent() throws Exception {
        MvcResult unassigned = create(null, START, 4)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationCode").value(org.hamcrest.Matchers.startsWith("RSV-")))
                .andExpect(jsonPath("$.restaurantTable").doesNotExist())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        long unassignedId = number(unassigned, "$.id");
        long unassignedVersion = number(unassigned, "$.version");

        MvcResult reassigned = update(unassignedId, unassignedVersion, secondTableId, START.plusSeconds(7200), 5)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantTable.id").value(secondTableId))
                .andReturn();
        long reassignedVersion = number(reassigned, "$.version");

        update(unassignedId, unassignedVersion, secondTableId, START.plusSeconds(7200), 5)
                .andExpect(status().isConflict());

        MvcResult assigned = create(largeTableId, START, 4)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.restaurantTable.tableNumber").value("R-01"))
                .andReturn();
        long assignedId = number(assigned, "$.id");
        long assignedVersion = number(assigned, "$.version");

        MvcResult confirmed = transition(assignedId, assignedVersion, "CONFIRMED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn();
        long confirmedVersion = number(confirmed, "$.version");

        create(largeTableId, START.plusSeconds(1800), 4)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Restaurant table is unavailable for the requested time"));
        create(largeTableId, START.plusSeconds(5400), 4)
                .andExpect(status().isCreated());
        create(smallTableId, START, 4)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Restaurant table capacity is insufficient"));
        create(inactiveTableId, START, 4)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Restaurant table is inactive"));
        create(outOfServiceTableId, START, 4)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Restaurant table is out of service"));
        create(999999L, START, 4).andExpect(status().isNotFound());

        MvcResult seated = transition(assignedId, confirmedVersion, "SEATED")
                .andExpect(status().isOk())
                .andReturn();
        MvcResult completed = transition(assignedId, number(seated, "$.version"), "COMPLETED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn();
        transition(assignedId, number(completed, "$.version"), "CONFIRMED")
                .andExpect(status().isConflict());

        MvcResult cancelledCandidate = create(null, START.plusSeconds(10800), 2).andReturn();
        transition(number(cancelledCandidate, "$.id"), number(cancelledCandidate, "$.version"), "CANCELLED")
                .andExpect(status().isOk());

        MvcResult noShowCandidate = create(null, START.plusSeconds(14400), 2).andReturn();
        MvcResult noShowConfirmed = transition(
                        number(noShowCandidate, "$.id"), number(noShowCandidate, "$.version"), "CONFIRMED")
                .andReturn();
        transition(number(noShowConfirmed, "$.id"), number(noShowConfirmed, "$.version"), "NO_SHOW")
                .andExpect(status().isOk());

        MvcResult seatedCancellation = create(null, START.plusSeconds(18000), 2).andReturn();
        MvcResult seatedCancellationConfirmed = transition(
                        number(seatedCancellation, "$.id"),
                        number(seatedCancellation, "$.version"),
                        "CONFIRMED")
                .andReturn();
        MvcResult seatedCancellationSeated = transition(
                        number(seatedCancellationConfirmed, "$.id"),
                        number(seatedCancellationConfirmed, "$.version"),
                        "SEATED")
                .andReturn();
        transition(
                        number(seatedCancellationSeated, "$.id"),
                        number(seatedCancellationSeated, "$.version"),
                        "CANCELLED")
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/reservations")
                        .with(adminJwt())
                        .queryParam("assigned", "true")
                        .queryParam("tableId", secondTableId.toString())
                        .queryParam("guestName", "Ada")
                        .queryParam("reservationCode", text(reassigned, "$.reservationCode"))
                        .queryParam("startFrom", START.plusSeconds(3600).toString())
                        .queryParam("startTo", START.plusSeconds(10800).toString())
                        .queryParam("sortBy", "partySize")
                        .queryParam("direction", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(unassignedId));

        mockMvc.perform(get("/api/v1/reservations/availability")
                        .with(adminJwt())
                        .queryParam("startAt", START.toString())
                        .queryParam("durationMinutes", "90")
                        .queryParam("partySize", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + largeTableId + ")]").isNotEmpty());

        mockMvc.perform(get("/api/v1/reservations/{id}", 999999).with(adminJwt()))
                .andExpect(status().isNotFound());

        assertThat(reassignedVersion).isGreaterThan(unassignedVersion);
        assertAuditActions(
                "RESERVATION_CREATED",
                "RESERVATION_UPDATED",
                "RESERVATION_TABLE_ASSIGNED",
                "RESERVATION_CONFIRMED",
                "RESERVATION_SEATED",
                "RESERVATION_COMPLETED",
                "RESERVATION_CANCELLED",
                "RESERVATION_NO_SHOW");
    }

    @Test
    void simultaneousConfirmationsCannotCreateTwoBlockingOverlaps() throws Exception {
        MvcResult first = create(largeTableId, START, 4).andExpect(status().isCreated()).andReturn();
        MvcResult second = create(largeTableId, START.plusSeconds(900), 4)
                .andExpect(status().isCreated())
                .andReturn();
        var ready = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var statuses = Collections.synchronizedList(new ArrayList<Integer>());

        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<?>> futures = List.of(
                    executor.submit(() -> concurrentConfirm(first, ready, release, statuses)),
                    executor.submit(() -> concurrentConfirm(second, ready, release, statuses)));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        }

        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reservations WHERE restaurant_table_id = ? "
                        + "AND status IN ('CONFIRMED', 'SEATED')",
                Integer.class,
                largeTableId)).isEqualTo(1);
    }

    private void concurrentConfirm(
            MvcResult reservation,
            CountDownLatch ready,
            CountDownLatch release,
            List<Integer> statuses) {
        try {
            ready.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent test did not release requests");
            }
            MvcResult result = mockMvc.perform(patch("/api/v1/reservations/{id}/status", number(reservation, "$.id"))
                            .with(adminJwt())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"CONFIRMED\",\"version\":"
                                    + number(reservation, "$.version") + "}"))
                    .andReturn();
            statuses.add(result.getResponse().getStatus());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private org.springframework.test.web.servlet.ResultActions create(Long tableId, Instant startAt, int partySize)
            throws Exception {
        String assignment = tableId == null ? "null" : tableId.toString();
        return mockMvc.perform(post("/api/v1/reservations")
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "guestName": "Ada Guest",
                          "guestPhone": "+12025550123",
                          "guestEmail": "ada@example.com",
                          "partySize": %d,
                          "startAt": "%s",
                          "durationMinutes": 90,
                          "restaurantTableId": %s,
                          "notes": "Window requested"
                        }
                        """.formatted(partySize, startAt, assignment)));
    }

    private org.springframework.test.web.servlet.ResultActions update(
            long id,
            long version,
            Long tableId,
            Instant startAt,
            int partySize) throws Exception {
        return mockMvc.perform(put("/api/v1/reservations/{id}", id)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "guestName": "Ada Updated",
                          "guestPhone": "+12025550123",
                          "guestEmail": "ada@example.com",
                          "partySize": %d,
                          "startAt": "%s",
                          "durationMinutes": 120,
                          "restaurantTableId": %d,
                          "notes": "Reassigned",
                          "version": %d
                        }
                        """.formatted(partySize, startAt, tableId, version)));
    }

    private org.springframework.test.web.servlet.ResultActions transition(long id, long version, String target)
            throws Exception {
        return mockMvc.perform(patch("/api/v1/reservations/{id}/status", id)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + target + "\",\"version\":" + version + "}"));
    }

    private Long insertTable(String number, int capacity, boolean active, String status) {
        jdbcTemplate.update(
                "INSERT INTO restaurant_tables "
                        + "(table_number, display_name, capacity, section, status, active) "
                        + "VALUES (?, ?, ?, 'Main', ?, ?)",
                number,
                "Table " + number,
                capacity,
                status,
                active);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM restaurant_tables WHERE table_number = ?", Long.class, number);
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
            adminJwt() {
        return jwt().jwt(token -> token.subject(actorUserId.toString()).claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private long number(MvcResult result, String path) throws java.io.UnsupportedEncodingException {
        Number value = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path);
        return value.longValue();
    }

    private String text(MvcResult result, String path) throws java.io.UnsupportedEncodingException {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private void assertAuditActions(String... actions) {
        for (String action : actions) {
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs "
                            + "WHERE action = ? AND actor_user_id = ? AND entity_type = 'RESERVATION'",
                    Integer.class,
                    action,
                    actorUserId)).isGreaterThanOrEqualTo(1);
        }
    }
}
