package com.adam.restaurantoperations.tables;

import java.time.Instant;
import java.util.List;

import com.adam.restaurantoperations.audit.AuthenticationAuditService;
import com.adam.restaurantoperations.audit.MenuAuditService;
import com.adam.restaurantoperations.audit.OrderAuditService;
import com.adam.restaurantoperations.audit.ReservationAuditService;
import com.adam.restaurantoperations.audit.TableAuditService;
import com.adam.restaurantoperations.auth.service.AuthenticationService;
import com.adam.restaurantoperations.menu.MenuService;
import com.adam.restaurantoperations.orders.OrderService;
import com.adam.restaurantoperations.reservations.ReservationService;
import com.adam.restaurantoperations.tables.dto.CreateTableRequest;
import com.adam.restaurantoperations.tables.dto.TableResponse;
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
class RestaurantTableControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RestaurantTableService service;

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private AuthenticationAuditService authenticationAuditService;

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
    void everyTableEndpointRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/tables"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/v1/tables").with(jwt().jwt(token -> token
                                .subject("7")
                                .claim("roles", List.of("SERVER")))
                        .authorities(new SimpleGrantedAuthority("ROLE_SERVER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        given(service.list(any(), any(), any(), any(), any(), any())).willReturn(List.of());
        mockMvc.perform(get("/api/v1/tables").with(adminJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void createValidatesInputAndMalformedJsonSafely() throws Exception {
        mockMvc.perform(post("/api/v1/tables")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tableNumber": "",
                                  "displayName": "",
                                  "capacity": 0,
                                  "section": "",
                                  "status": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.tableNumber").exists())
                .andExpect(jsonPath("$.fieldErrors.displayName").exists())
                .andExpect(jsonPath("$.fieldErrors.section").exists())
                .andExpect(jsonPath("$.fieldErrors.status").exists());

        mockMvc.perform(post("/api/v1/tables")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableNumber\":not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or unreadable JSON"))
                .andExpect(jsonPath("$.trace").doesNotExist());

        mockMvc.perform(get("/api/v1/tables")
                        .with(adminJwt())
                        .queryParam("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid request parameter"));
    }

    @Test
    void adminCanCreateAndReceivesLocation() throws Exception {
        given(service.create(any(CreateTableRequest.class), any(), any())).willReturn(table());

        mockMvc.perform(post("/api/v1/tables")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "tableNumber": "T-01",
                                  "displayName": "Window",
                                  "capacity": 4,
                                  "section": "Main",
                                  "status": "AVAILABLE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.tableNumber").value("T-01"));
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
            adminJwt() {
        return jwt().jwt(token -> token.subject("7").claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private TableResponse table() {
        return new TableResponse(
                12L,
                "T-01",
                "Window",
                4,
                "Main",
                TableStatus.AVAILABLE,
                true,
                Instant.parse("2026-08-04T10:00:00Z"),
                Instant.parse("2026-08-04T10:00:00Z"),
                0L);
    }
}
