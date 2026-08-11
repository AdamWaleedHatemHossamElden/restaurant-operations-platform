package com.adam.restaurantoperations.menu;

import java.util.List;

import com.adam.restaurantoperations.audit.AuthenticationAuditService;
import com.adam.restaurantoperations.audit.MenuAuditService;
import com.adam.restaurantoperations.audit.KitchenAuditService;
import com.adam.restaurantoperations.audit.OrderAuditService;
import com.adam.restaurantoperations.audit.ReservationAuditService;
import com.adam.restaurantoperations.audit.TableAuditService;
import com.adam.restaurantoperations.auth.service.AuthenticationService;
import com.adam.restaurantoperations.reservations.ReservationService;
import com.adam.restaurantoperations.kitchen.KitchenService;
import com.adam.restaurantoperations.orders.OrderService;
import com.adam.restaurantoperations.tables.RestaurantTableService;
import com.adam.restaurantoperations.testsupport.MockInventoryBeans;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.frontend-origin=http://localhost:5173")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@MockInventoryBeans
class MenuControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MenuService menuService;

    @MockitoBean
    private MenuAuditService menuAuditService;

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private AuthenticationAuditService authenticationAuditService;

    @MockitoBean
    private RestaurantTableService restaurantTableService;

    @MockitoBean
    private TableAuditService tableAuditService;

    @MockitoBean
    private ReservationService reservationService;

    @MockitoBean
    private ReservationAuditService reservationAuditService;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private OrderAuditService orderAuditService;

    @MockitoBean
    private KitchenService kitchenService;

    @MockitoBean
    private KitchenAuditService kitchenAuditService;

    @Test
    void menuEndpointsRequireAuthenticationAndAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/menu/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/v1/menu/categories").with(jwt()
                        .jwt(token -> token.subject("7").claim("roles", List.of("SERVER")))
                        .authorities(new SimpleGrantedAuthority("ROLE_SERVER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        given(menuService.listCategories(any(), any(), any(), any())).willReturn(List.of());
        mockMvc.perform(get("/api/v1/menu/categories").with(adminJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void validationMalformedJsonAndInvalidFiltersReturnSafeBadRequests() throws Exception {
        mockMvc.perform(post("/api/v1/menu/categories")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"displayOrder\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.displayOrder").exists());

        mockMvc.perform(post("/api/v1/menu/items")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or unreadable JSON"))
                .andExpect(jsonPath("$.trace").doesNotExist());

        mockMvc.perform(get("/api/v1/menu/modifier-groups")
                        .with(adminJwt())
                        .queryParam("selectionType", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request parameter"));
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
            adminJwt() {
        return jwt().jwt(token -> token.subject("7").claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
