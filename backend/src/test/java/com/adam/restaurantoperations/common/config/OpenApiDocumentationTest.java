package com.adam.restaurantoperations.common.config;

import com.adam.restaurantoperations.audit.AuthenticationAuditService;
import com.adam.restaurantoperations.audit.MenuAuditService;
import com.adam.restaurantoperations.audit.OrderAuditService;
import com.adam.restaurantoperations.audit.ReservationAuditService;
import com.adam.restaurantoperations.audit.TableAuditService;
import com.adam.restaurantoperations.auth.service.AuthenticationService;
import com.adam.restaurantoperations.menu.MenuService;
import com.adam.restaurantoperations.orders.OrderService;
import com.adam.restaurantoperations.reservations.ReservationService;
import com.adam.restaurantoperations.roles.RoleRepository;
import com.adam.restaurantoperations.tables.RestaurantTableService;
import com.adam.restaurantoperations.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "app.frontend-origin=http://localhost:5173",
    "springdoc.api-docs.enabled=true",
    "springdoc.swagger-ui.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RoleRepository roleRepository;

    @MockitoBean
    private AuthenticationAuditService auditService;

    @MockitoBean
    private RestaurantTableService restaurantTableService;

    @MockitoBean
    private TableAuditService tableAuditService;

    @MockitoBean
    private ReservationService reservationService;

    @MockitoBean
    private ReservationAuditService reservationAuditService;

    @MockitoBean
    private MenuService menuService;

    @MockitoBean
    private MenuAuditService menuAuditService;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private OrderAuditService orderAuditService;

    @Test
    void generatedDocumentDefinesBearerSchemeForAuthenticatedOperations() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/me'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/tables'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/tables'].post.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/tables/{id}'].put.security[0].bearerAuth").isArray())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/tables/{id}/activation'].patch.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/tables'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/tables'].post.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/tables/{id}'].put.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reservations'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/reservations'].post.responses['201']").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/v1/reservations/{id}/status'].patch.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reservations/availability'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/menu/categories'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/menu/categories'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/menu/items'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/menu/items/{id}/modifier-groups'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/menu/modifier-groups'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/menu/modifier-groups/{groupId}/options'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}/items'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}/items/{itemId}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{id}/status'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/health'].get.security").doesNotExist());
    }

    @Test
    void swaggerUiLoadsUnderDevelopmentProfile() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
