package com.adam.restaurantoperations.common.health;

import com.adam.restaurantoperations.audit.AuthenticationAuditService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.frontend-origin=http://localhost:5173")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HealthService healthService;

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
    private MenuService menuService;

    @MockitoBean
    private MenuAuditService menuAuditService;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private OrderAuditService orderAuditService;

    @Test
    void returnsTypedHealthResponseWithoutAuthentication() throws Exception {
        given(healthService.currentStatus())
                .willReturn(new HealthResponse("UP", "restaurant-operations-backend"));

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("restaurant-operations-backend"));
    }
}
