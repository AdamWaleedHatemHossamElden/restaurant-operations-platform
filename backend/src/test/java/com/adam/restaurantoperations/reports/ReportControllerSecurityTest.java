package com.adam.restaurantoperations.reports;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(properties = "app.frontend-origin=http://localhost:5173")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@MockInventoryBeans
class ReportControllerSecurityTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ReportService reportService;
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
    void reportsRequireAdminAndOperationalRolesDoNotGrantAccess() throws Exception {
        String path = "/api/v1/reports/overview?from=2030-01-01T00:00:00Z&to=2030-01-02T00:00:00Z";
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        for (String role : List.of("HOST", "WAITER", "CASHIER", "KITCHEN", "MANAGER")) {
            mockMvc.perform(get(path).with(jwt()
                            .jwt(token -> token.subject("7").claim("roles", List.of(role)))
                            .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                    .andExpect(status().isForbidden());
        }
        given(reportService.overview(any())).willReturn(null);
        mockMvc.perform(get(path).with(jwt()
                        .jwt(token -> token.subject("7").claim("roles", List.of("ADMIN")))
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void reportFiltersReturnSafeBadRequests() throws Exception {
        var admin = jwt()
                .jwt(token -> token.subject("7").claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mockMvc.perform(get("/api/v1/reports/overview")
                        .param("to", "2030-01-02T00:00:00Z")
                        .with(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mockMvc.perform(get("/api/v1/reports/overview")
                        .param("from", "2030-01-02T00:00:00Z")
                        .param("to", "2030-01-02T00:00:00Z")
                        .with(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("from must be before to"));
        mockMvc.perform(get("/api/v1/reports/overview")
                        .param("from", "2030-01-02T00:00:00Z")
                        .param("to", "2032-01-02T00:00:00Z")
                        .with(admin))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/reports/sales")
                        .param("from", "2030-01-01T00:00:00Z")
                        .param("to", "2030-01-02T00:00:00Z")
                        .param("groupBy", "UNSAFE")
                        .with(admin))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/reports/sales")
                        .param("from", "2030-01-01T00:00:00Z")
                        .param("to", "2030-01-02T00:00:00Z")
                        .param("top", "101")
                        .with(admin))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/reports/exports/unknown.csv")
                        .param("from", "2030-01-01T00:00:00Z")
                        .param("to", "2030-01-02T00:00:00Z")
                        .with(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unsupported report export"));
    }
}
