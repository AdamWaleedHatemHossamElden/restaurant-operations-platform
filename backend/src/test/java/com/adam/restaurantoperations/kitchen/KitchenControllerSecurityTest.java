package com.adam.restaurantoperations.kitchen;

import java.util.List;

import com.adam.restaurantoperations.audit.AuthenticationAuditService;
import com.adam.restaurantoperations.audit.KitchenAuditService;
import com.adam.restaurantoperations.audit.MenuAuditService;
import com.adam.restaurantoperations.audit.OrderAuditService;
import com.adam.restaurantoperations.audit.ReservationAuditService;
import com.adam.restaurantoperations.audit.TableAuditService;
import com.adam.restaurantoperations.auth.service.AuthenticationService;
import com.adam.restaurantoperations.menu.MenuService;
import com.adam.restaurantoperations.orders.OrderService;
import com.adam.restaurantoperations.reservations.ReservationService;
import com.adam.restaurantoperations.tables.RestaurantTableService;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.frontend-origin=http://localhost:5173")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KitchenControllerSecurityTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private KitchenService kitchenService;
    @MockitoBean private KitchenAuditService kitchenAuditService;
    @MockitoBean private OrderService orderService;
    @MockitoBean private OrderAuditService orderAuditService;
    @MockitoBean private AuthenticationService authenticationService;
    @MockitoBean private AuthenticationAuditService authenticationAuditService;
    @MockitoBean private RestaurantTableService restaurantTableService;
    @MockitoBean private TableAuditService tableAuditService;
    @MockitoBean private ReservationService reservationService;
    @MockitoBean private ReservationAuditService reservationAuditService;
    @MockitoBean private MenuService menuService;
    @MockitoBean private MenuAuditService menuAuditService;

    @Test
    void kitchenEndpointsRequireAuthenticationAndAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/kitchen/tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/v1/kitchen/tickets").with(jwt()
                        .jwt(token -> token.subject("7").claim("roles", List.of("SERVER")))
                        .authorities(new SimpleGrantedAuthority("ROLE_SERVER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        given(kitchenService.list(any(), any(), any(), any(), any(), anyBoolean(), any(), any()))
                .willReturn(List.of());
        mockMvc.perform(get("/api/v1/kitchen/tickets").with(adminJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void malformedAndInvalidKitchenRequestsUseSafeErrors() throws Exception {
        mockMvc.perform(patch("/api/v1/kitchen/tickets/1/items/2/status")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or unreadable JSON"))
                .andExpect(jsonPath("$.trace").doesNotExist());

        mockMvc.perform(patch("/api/v1/kitchen/tickets/1/items/2/status")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"READY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.version").exists());

        mockMvc.perform(get("/api/v1/kitchen/tickets")
                        .with(adminJwt())
                        .queryParam("status", "COOKING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request parameter"));
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
            adminJwt() {
        return jwt().jwt(token -> token.subject("7").claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
