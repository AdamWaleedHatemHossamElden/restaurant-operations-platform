package com.adam.restaurantoperations.infrastructure;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
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
class MenuManagementIT {

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

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    private Long actorUserId;

    @BeforeEach
    void cleanAndSeedActor() {
        jdbcTemplate.update("DELETE FROM menu_item_modifier_groups");
        jdbcTemplate.update("DELETE FROM modifier_options");
        jdbcTemplate.update("DELETE FROM menu_items");
        jdbcTemplate.update("DELETE FROM modifier_groups");
        jdbcTemplate.update("DELETE FROM menu_categories");
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM audit_logs");
        jdbcTemplate.update("DELETE FROM user_roles");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("DELETE FROM roles");
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, display_name, enabled) VALUES (?, ?, ?, TRUE)",
                "menu-admin@example.com", "integration-test-password-hash", "Menu Admin");
        actorUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'menu-admin@example.com'", Long.class);
    }

    @Test
    void categoryAndItemLifecycleNormalizationFilteringAvailabilityAndAuditRemainConsistent()
            throws Exception {
        MvcResult category = createCategory("  Hot   Drinks ", 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Hot Drinks"))
                .andReturn();
        long categoryId = number(category, "$.id");
        long categoryVersion = number(category, "$.version");

        createCategory("hot drinks", 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));

        MvcResult item = createItem(categoryId, " iced  latte ", "4.20")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ICED-LATTE"))
                .andExpect(jsonPath("$.basePrice").value("4.20"))
                .andExpect(jsonPath("$.effectivelyAvailable").value(true))
                .andReturn();
        long itemId = number(item, "$.id");
        long itemVersion = number(item, "$.version");

        createItem(categoryId, "ICED-LATTE", "5.00")
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/menu/items").with(adminJwt())
                        .queryParam("categoryId", Long.toString(categoryId))
                        .queryParam("effectivelyAvailable", "true")
                        .queryParam("search", "latte")
                        .queryParam("sortBy", "basePrice")
                        .queryParam("direction", "DESC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(itemId));

        MvcResult unavailable = flag("/api/v1/menu/items/{id}/availability", itemId, false, itemVersion)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableForSale").value(false))
                .andExpect(jsonPath("$.effectivelyAvailable").value(false))
                .andReturn();
        long unavailableVersion = number(unavailable, "$.version");

        flag("/api/v1/menu/items/{id}/activation", itemId, false, unavailableVersion)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        flag("/api/v1/menu/categories/{id}/activation", categoryId, false, categoryVersion)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(put("/api/v1/menu/categories/{id}", categoryId)
                        .with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Coffee","description":"Freshly brewed","displayOrder":1,
                                 "version":%d}
                                """.formatted(categoryVersion)))
                .andExpect(status().isConflict());

        assertAudit("MENU_CATEGORY_CREATED", "MENU_ITEM_CREATED",
                "MENU_ITEM_AVAILABILITY_CHANGED", "MENU_ITEM_DEACTIVATED", "MENU_CATEGORY_DEACTIVATED");
    }

    @Test
    void reusableModifierAssignmentsPreserveOrderAndRejectUnsafeConfiguration() throws Exception {
        long categoryId = number(createCategory("Food", 0).andReturn(), "$.id");
        MvcResult item = createItem(categoryId, "BURGER", "12.50").andReturn();
        long itemId = number(item, "$.id");
        long itemVersion = number(item, "$.version");

        MvcResult size = createGroup("Size", "SINGLE", 1, 1, 0).andReturn();
        long sizeId = number(size, "$.id");
        long sizeVersion = number(size, "$.version");
        MvcResult extras = createGroup("Extras", "MULTIPLE", 0, 2, 1).andReturn();
        long extrasId = number(extras, "$.id");

        createGroup("Invalid", "SINGLE", 0, 2, 0)
                .andExpect(status().isConflict());
        createOption(sizeId, "Regular", "0.00", 0).andExpect(status().isCreated());
        MvcResult large = createOption(sizeId, "Large", "2.50", 1)
                .andExpect(status().isCreated()).andReturn();
        createOption(sizeId, "regular", "1.00", 2).andExpect(status().isConflict());
        createOption(extrasId, "Cheese", "1.20", 0).andExpect(status().isCreated());
        MvcResult bacon = createOption(extrasId, "Bacon", "2.00", 1)
                .andExpect(status().isCreated()).andReturn();
        long baconId = number(bacon, "$.id");
        long baconVersion = number(bacon, "$.version");

        MvcResult assigned = mockMvc.perform(put("/api/v1/menu/items/{id}/modifier-groups", itemId)
                        .with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":%d,"assignments":[
                                  {"modifierGroupId":%d,"displayOrder":0},
                                  {"modifierGroupId":%d,"displayOrder":1}]}
                                """.formatted(itemVersion, extrasId, sizeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modifierGroups[0].modifierGroupId").value(extrasId))
                .andExpect(jsonPath("$.modifierGroups[1].modifierGroupId").value(sizeId))
                .andReturn();
        long assignedVersion = number(assigned, "$.version");

        mockMvc.perform(put("/api/v1/menu/items/{id}/modifier-groups", itemId)
                        .with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":%d,"assignments":[
                                  {"modifierGroupId":%d,"displayOrder":0},
                                  {"modifierGroupId":%d,"displayOrder":0}]}
                                """.formatted(assignedVersion, sizeId, extrasId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Modifier-group display orders must be unique"));

        mockMvc.perform(put("/api/v1/menu/items/{id}/modifier-groups", itemId)
                        .with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":%d,"assignments":[
                                  {"modifierGroupId":%d,"displayOrder":0},
                                  {"modifierGroupId":%d,"displayOrder":1}]}
                                """.formatted(assignedVersion, sizeId, sizeId)))
                .andExpect(status().isConflict());

        flag("/api/v1/menu/modifier-options/{id}/activation", baconId, false, baconVersion)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Modifier configuration cannot satisfy its active selection rules"));

        flag("/api/v1/menu/modifier-groups/{id}/activation", sizeId, false, sizeVersion)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/v1/menu/items/{id}", itemId).with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modifierGroups[1].active").value(false));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM menu_item_modifier_groups WHERE menu_item_id = ?",
                Integer.class, itemId)).isEqualTo(2);
        assertAudit("MODIFIER_GROUP_CREATED", "MODIFIER_OPTION_CREATED", "MENU_ITEM_MODIFIERS_UPDATED");
    }

    @Test
    void concurrentOptionDeactivationsSerializeOnTheModifierGroupAggregate() throws Exception {
        long categoryId = number(createCategory("Dinner", 0).andReturn(), "$.id");
        MvcResult item = createItem(categoryId, "STEAK", "24.00").andReturn();
        long itemId = number(item, "$.id");
        long itemVersion = number(item, "$.version");
        long groupId = number(createGroup("Sides", "MULTIPLE", 2, 2, 0).andReturn(), "$.id");
        MvcResult firstOption = createOption(groupId, "Potatoes", "0.00", 0).andReturn();
        MvcResult secondOption = createOption(groupId, "Vegetables", "0.00", 1).andReturn();
        createOption(groupId, "Rice", "0.00", 2).andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/menu/items/{id}/modifier-groups", itemId)
                        .with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":%d,"assignments":[
                                  {"modifierGroupId":%d,"displayOrder":0}]}
                                """.formatted(itemVersion, groupId)))
                .andExpect(status().isOk());

        CyclicBarrier start = new CyclicBarrier(3);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> first = executor.submit(() -> concurrentDeactivation(
                    start, number(firstOption, "$.id"), number(firstOption, "$.version")));
            Future<Integer> second = executor.submit(() -> concurrentDeactivation(
                    start, number(secondOption, "$.id"), number(secondOption, "$.version")));
            start.await(10, TimeUnit.SECONDS);

            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM modifier_options WHERE modifier_group_id = ? AND active = TRUE",
                Integer.class, groupId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'MODIFIER_OPTION_DEACTIVATED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void itemReactivationRejectsAnUnusableAssignedGroupWithoutWritingSuccessAudit() throws Exception {
        long categoryId = number(createCategory("Lunch", 0).andReturn(), "$.id");
        MvcResult item = createItem(categoryId, "WRAP", "8.50").andReturn();
        long itemId = number(item, "$.id");
        long itemVersion = number(item, "$.version");
        long groupId = number(createGroup("Fillings", "MULTIPLE", 2, 2, 0).andReturn(), "$.id");
        createOption(groupId, "Salad", "0.00", 0).andExpect(status().isCreated());
        MvcResult chicken = createOption(groupId, "Chicken", "1.50", 1).andReturn();

        MvcResult assigned = mockMvc.perform(put("/api/v1/menu/items/{id}/modifier-groups", itemId)
                        .with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":%d,"assignments":[
                                  {"modifierGroupId":%d,"displayOrder":0}]}
                                """.formatted(itemVersion, groupId)))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult inactive = flag("/api/v1/menu/items/{id}/activation", itemId, false,
                        number(assigned, "$.version"))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult deactivatedOption = flag("/api/v1/menu/modifier-options/{id}/activation",
                        number(chicken, "$.id"), false, number(chicken, "$.version"))
                .andExpect(status().isOk())
                .andReturn();

        flag("/api/v1/menu/items/{id}/activation", itemId, true, number(inactive, "$.version"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Modifier configuration cannot satisfy its active selection rules"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT active FROM menu_items WHERE id = ?", Boolean.class, itemId)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'MENU_ITEM_REACTIVATED'",
                Integer.class)).isZero();

        flag("/api/v1/menu/modifier-options/{id}/activation", number(chicken, "$.id"), true,
                        number(deactivatedOption, "$.version"))
                .andExpect(status().isOk());
        flag("/api/v1/menu/items/{id}/activation", itemId, true, number(inactive, "$.version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'MENU_ITEM_REACTIVATED'",
                Integer.class)).isEqualTo(1);
    }

    private int concurrentDeactivation(CyclicBarrier start, long optionId, long version) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        return flag("/api/v1/menu/modifier-options/{id}/activation", optionId, false, version)
                .andReturn().getResponse().getStatus();
    }

    private org.springframework.test.web.servlet.ResultActions createCategory(String name, int order)
            throws Exception {
        return mockMvc.perform(post("/api/v1/menu/categories").with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"displayOrder\":" + order + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions createItem(long categoryId, String code, String price)
            throws Exception {
        return mockMvc.perform(post("/api/v1/menu/items").with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"categoryId":%d,"code":"%s","name":"Iced Latte",
                         "basePrice":"%s","displayOrder":0}
                        """.formatted(categoryId, code, price)));
    }

    private org.springframework.test.web.servlet.ResultActions createGroup(
            String name, String type, int minimum, int maximum, int order) throws Exception {
        return mockMvc.perform(post("/api/v1/menu/modifier-groups").with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","selectionType":"%s","minimumSelections":%d,
                         "maximumSelections":%d,"displayOrder":%d}
                        """.formatted(name, type, minimum, maximum, order)));
    }

    private org.springframework.test.web.servlet.ResultActions createOption(
            long groupId, String name, String price, int order) throws Exception {
        return mockMvc.perform(post("/api/v1/menu/modifier-groups/{id}/options", groupId)
                .with(adminJwt()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","priceAdjustment":"%s","displayOrder":%d}
                        """.formatted(name, price, order)));
    }

    private org.springframework.test.web.servlet.ResultActions flag(
            String path, long id, boolean value, long version) throws Exception {
        return mockMvc.perform(patch(path, id).with(adminJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":" + value + ",\"version\":" + version + "}"));
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

    private void assertAudit(String... actions) {
        for (String action : actions) {
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE action = ? AND actor_user_id = ?",
                    Integer.class, action, actorUserId)).isGreaterThanOrEqualTo(1);
        }
    }
}
