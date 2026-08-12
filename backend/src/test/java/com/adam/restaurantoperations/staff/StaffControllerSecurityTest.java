package com.adam.restaurantoperations.staff;

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
class StaffControllerSecurityTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private StaffService staffService;

    @Test
    void everyStaffResourceRequiresAuthentication() throws Exception {
        for (MockHttpServletRequestBuilder request : representativeRequests()) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void operationalRolesNeverGrantApplicationAuthorization() throws Exception {
        for (OperationalRole role : OperationalRole.values()) {
            mockMvc.perform(get("/api/v1/staff/employees").with(jwt()
                            .jwt(token -> token.subject("7").claim("roles", List.of(role.name())))
                            .authorities(new SimpleGrantedAuthority("ROLE_" + role.name()))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminCanReachStaffResources() throws Exception {
        given(staffService.listEmployees(any(), any(), any(), any(), any())).willReturn(List.of());
        given(staffService.listShifts(any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(List.of());

        mockMvc.perform(get("/api/v1/staff/employees").with(adminJwt())).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/staff/shifts").with(adminJwt())).andExpect(status().isOk());
    }

    @Test
    void malformedAndInvalidRequestsUseSafeErrors() throws Exception {
        mockMvc.perform(post("/api/v1/staff/employees")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeCode\": EMP001}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or unreadable JSON"));

        mockMvc.perform(post("/api/v1/staff/shifts")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"employeeId":1,"operationalRole":"INVALID","startAt":"2026-08-12T09:00:00Z",
                                 "endAt":"2026-08-12T17:00:00Z"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private MockHttpServletRequestBuilder[] representativeRequests() {
        return new MockHttpServletRequestBuilder[] {
            get("/api/v1/staff/employees"),
            get("/api/v1/staff/employees/1/availability")
                    .param("startAt", "2026-08-11T00:00:00Z")
                    .param("endAt", "2026-08-18T00:00:00Z"),
            get("/api/v1/staff/shifts")
        };
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt()
                .jwt(token -> token.subject("7").claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
