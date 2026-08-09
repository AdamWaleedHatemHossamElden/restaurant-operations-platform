package com.adam.restaurantoperations.orders;

import java.util.List;

import com.adam.restaurantoperations.audit.AuthenticationAuditService;
import com.adam.restaurantoperations.audit.MenuAuditService;
import com.adam.restaurantoperations.audit.OrderAuditService;
import com.adam.restaurantoperations.audit.ReservationAuditService;
import com.adam.restaurantoperations.audit.TableAuditService;
import com.adam.restaurantoperations.auth.service.AuthenticationService;
import com.adam.restaurantoperations.menu.MenuService;
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
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.frontend-origin=http://localhost:5173")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderControllerSecurityTest {
    @Autowired private MockMvc mockMvc;
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
    void orderEndpointsRequireAuthenticationAndAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/v1/orders").with(jwt()
                        .jwt(token -> token.subject("7").claim("roles", List.of("SERVER")))
                        .authorities(new SimpleGrantedAuthority("ROLE_SERVER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        given(orderService.list(any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(List.of());
        mockMvc.perform(get("/api/v1/orders").with(adminJwt())).andExpect(status().isOk());
    }

    @Test
    void validationMalformedJsonAndInvalidFiltersAreSafe() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"missing table\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.restaurantTableId").exists());

        mockMvc.perform(post("/api/v1/orders")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantTableId\":not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or unreadable JSON"))
                .andExpect(jsonPath("$.trace").doesNotExist());

        mockMvc.perform(get("/api/v1/orders")
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
