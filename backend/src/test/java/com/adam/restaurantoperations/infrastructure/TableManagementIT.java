package com.adam.restaurantoperations.infrastructure;

import java.util.List;

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
class TableManagementIT {

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

    @BeforeEach
    void cleanAndSeedActor() {
        jdbcTemplate.update("DELETE FROM restaurant_tables");
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM user_roles");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("DELETE FROM roles");
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, display_name, enabled) VALUES (?, ?, ?, TRUE)",
                "table-admin@example.com",
                "integration-test-password-hash",
                "Table Admin");
        actorUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'table-admin@example.com'",
                Long.class);
    }

    @Test
    void crudFiltersConflictsSoftActivationAndAuditRemainConsistent() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/tables")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("t-01", "Window", 4, "Main")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tableNumber").value("T-01"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").isString())
                .andExpect(jsonPath("$.updatedAt").isString())
                .andReturn();
        long id = number(created, "$.id");
        long version = number(created, "$.version");

        mockMvc.perform(post("/api/v1/tables")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createJson("T-01", "Duplicate", 2, "Bar")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Table number is already in use"));

        mockMvc.perform(get("/api/v1/tables")
                        .with(adminJwt())
                        .queryParam("active", "true")
                        .queryParam("section", "Main")
                        .queryParam("status", "AVAILABLE")
                        .queryParam("tableNumber", "T-0")
                        .queryParam("sortBy", "capacity")
                        .queryParam("direction", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));

        MvcResult updated = mockMvc.perform(put("/api/v1/tables/{id}", id)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(version)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Garden table"))
                .andExpect(jsonPath("$.capacity").value(6))
                .andReturn();
        long updatedVersion = number(updated, "$.version");

        mockMvc.perform(put("/api/v1/tables/{id}", id)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson(version)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Restaurant table was changed by another request; reload and retry"));

        MvcResult deactivated = activation(id, false, updatedVersion)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andReturn();
        long inactiveVersion = number(deactivated, "$.version");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM restaurant_tables WHERE id = ? AND active = FALSE",
                Integer.class,
                id)).isEqualTo(1);

        activation(id, true, inactiveVersion)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/api/v1/tables/{id}", id).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableNumber").value("T-01"));
        mockMvc.perform(get("/api/v1/tables/{id}", 999999).with(adminJwt()))
                .andExpect(status().isNotFound());

        assertAuditActions("TABLE_CREATED", "TABLE_UPDATED", "TABLE_DEACTIVATED", "TABLE_REACTIVATED");
    }

    private org.springframework.test.web.servlet.ResultActions activation(long id, boolean active, long version)
            throws Exception {
        return mockMvc.perform(patch("/api/v1/tables/{id}/activation", id)
                .with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":" + active + ",\"version\":" + version + "}"));
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

    private String createJson(String tableNumber, String displayName, int capacity, String section) {
        return """
                {
                  "tableNumber": "%s",
                  "displayName": "%s",
                  "capacity": %d,
                  "section": "%s",
                  "status": "AVAILABLE"
                }
                """.formatted(tableNumber, displayName, capacity, section);
    }

    private String updateJson(long version) {
        return """
                {
                  "tableNumber": "T-01",
                  "displayName": "Garden table",
                  "capacity": 6,
                  "section": "Garden",
                  "status": "AVAILABLE",
                  "version": %d
                }
                """.formatted(version);
    }

    private void assertAuditActions(String... actions) {
        for (String action : actions) {
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs "
                            + "WHERE action = ? AND actor_user_id = ? AND entity_type = 'RESTAURANT_TABLE'",
                    Integer.class,
                    action,
                    actorUserId)).isEqualTo(1);
        }
    }
}
