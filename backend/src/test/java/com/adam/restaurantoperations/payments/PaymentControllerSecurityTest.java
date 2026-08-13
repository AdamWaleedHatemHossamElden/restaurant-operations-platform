package com.adam.restaurantoperations.payments;

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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.frontend-origin=http://localhost:5173")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@MockInventoryBeans
class PaymentControllerSecurityTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private PaymentService paymentService;
    @Autowired private InvoiceService invoiceService;
    @MockitoBean private AuthenticationService authenticationService;
    @MockitoBean private AuthenticationAuditService authenticationAuditService;
    @MockitoBean private RestaurantTableService restaurantTableService;
    @MockitoBean private TableAuditService tableAuditService;
    @MockitoBean private ReservationService reservationService;
    @MockitoBean private ReservationAuditService reservationAuditService;
    @MockitoBean private MenuService menuService;
    @MockitoBean private MenuAuditService menuAuditService;
    @MockitoBean private OrderService orderService;
    @MockitoBean private OrderAuditService orderAuditService;
    @MockitoBean private KitchenService kitchenService;
    @MockitoBean private KitchenAuditService kitchenAuditService;

    @Test
    void paymentAndInvoiceEndpointsRequireAuthenticationAndAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/payments")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/invoices")).andExpect(status().isUnauthorized());

        for (String role : List.of("HOST", "WAITER", "CASHIER", "KITCHEN", "MANAGER")) {
            mockMvc.perform(get("/api/v1/payments").with(jwt()
                            .jwt(token -> token.subject("7").claim("roles", List.of(role)))
                            .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/invoices").with(jwt()
                            .jwt(token -> token.subject("7").claim("roles", List.of(role)))
                            .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                    .andExpect(status().isForbidden());
        }

        given(paymentService.list(any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(List.of());
        given(invoiceService.list(any(), any(), any(), any(), any())).willReturn(List.of());
        mockMvc.perform(get("/api/v1/payments").with(adminJwt())).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/invoices").with(adminJwt())).andExpect(status().isOk());
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
            adminJwt() {
        return jwt().jwt(token -> token.subject("7").claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
