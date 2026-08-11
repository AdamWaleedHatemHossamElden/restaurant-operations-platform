package com.adam.restaurantoperations.inventory;

import java.util.List;

import com.adam.restaurantoperations.audit.AuthenticationAuditService;
import com.adam.restaurantoperations.audit.KitchenAuditService;
import com.adam.restaurantoperations.audit.MenuAuditService;
import com.adam.restaurantoperations.audit.OrderAuditService;
import com.adam.restaurantoperations.audit.ReservationAuditService;
import com.adam.restaurantoperations.audit.TableAuditService;
import com.adam.restaurantoperations.auth.service.AuthenticationService;
import com.adam.restaurantoperations.kitchen.KitchenService;
import com.adam.restaurantoperations.menu.MenuService;
import com.adam.restaurantoperations.orders.OrderService;
import com.adam.restaurantoperations.reservations.ReservationService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
@MockitoBean(types = {
    AuthenticationService.class,
    AuthenticationAuditService.class,
    RestaurantTableService.class,
    TableAuditService.class,
    ReservationService.class,
    ReservationAuditService.class,
    MenuService.class,
    MenuAuditService.class,
    OrderService.class,
    OrderAuditService.class,
    KitchenService.class,
    KitchenAuditService.class
})
class InventoryControllerSecurityTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private InventoryService inventoryService;
    @Autowired private RecipeService recipeService;
    @Autowired private SupplierService supplierService;
    @Autowired private PurchaseOrderService purchaseOrderService;

    @Test
    void everyPhaseSixResourceRequiresAuthentication() throws Exception {
        for (MockHttpServletRequestBuilder request : representativeRequests()) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void everyPhaseSixResourceRequiresAdminRole() throws Exception {
        for (MockHttpServletRequestBuilder request : representativeRequests()) {
            mockMvc.perform(request.with(jwt()
                            .jwt(token -> token.subject("7").claim("roles", List.of("SERVER")))
                            .authorities(new SimpleGrantedAuthority("ROLE_SERVER"))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminCanReachEveryPhaseSixResource() throws Exception {
        given(inventoryService.list(any(), any(), any(), any(), any(), any())).willReturn(List.of());
        given(recipeService.list()).willReturn(List.of());
        given(supplierService.list(any(), any())).willReturn(List.of());
        given(purchaseOrderService.list(any(), any(), any())).willReturn(List.of());

        mockMvc.perform(get("/api/v1/inventory/items").with(adminJwt())).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/recipes").with(adminJwt())).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/suppliers").with(adminJwt())).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/purchase-orders").with(adminJwt())).andExpect(status().isOk());
    }

    @Test
    void validationAndMalformedJsonUseSafeErrors() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/items")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"FLOUR\",\"name\": flour}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed or unreadable JSON"));

        mockMvc.perform(post("/api/v1/inventory/movements")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inventoryItemId":1,"movementType":"ADJUSTMENT_IN","quantity":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private MockHttpServletRequestBuilder[] representativeRequests() {
        return new MockHttpServletRequestBuilder[] {
            get("/api/v1/inventory/items"),
            get("/api/v1/recipes"),
            get("/api/v1/suppliers"),
            get("/api/v1/purchase-orders")
        };
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt()
                .jwt(token -> token.subject("7").claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
