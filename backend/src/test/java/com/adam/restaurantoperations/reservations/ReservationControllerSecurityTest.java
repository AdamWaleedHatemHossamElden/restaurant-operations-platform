package com.adam.restaurantoperations.reservations;

import java.time.Instant;
import java.util.List;

import com.adam.restaurantoperations.audit.AuthenticationAuditService;
import com.adam.restaurantoperations.audit.ReservationAuditService;
import com.adam.restaurantoperations.audit.TableAuditService;
import com.adam.restaurantoperations.auth.service.AuthenticationService;
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
class ReservationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService service;

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private AuthenticationAuditService authenticationAuditService;

    @MockitoBean
    private TableAuditService tableAuditService;

    @MockitoBean
    private RestaurantTableService restaurantTableService;

    @MockitoBean
    private ReservationAuditService reservationAuditService;

    @Test
    void reservationEndpointsRequireAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/reservations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        mockMvc.perform(get("/api/v1/reservations").with(jwt().jwt(token -> token
                                .subject("7")
                                .claim("roles", List.of("SERVER")))
                        .authorities(new SimpleGrantedAuthority("ROLE_SERVER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        given(service.list(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(List.of());
        mockMvc.perform(get("/api/v1/reservations").with(adminJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void createValidatesPayloadAndMalformedJsonSafely() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "guestName": "",
                                  "guestPhone": "bad",
                                  "partySize": 0,
                                  "startAt": "2030-04-12T18:00:00Z",
                                  "durationMinutes": 5
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.guestName").exists())
                .andExpect(jsonPath("$.fieldErrors.guestPhone").exists())
                .andExpect(jsonPath("$.fieldErrors.partySize").exists())
                .andExpect(jsonPath("$.fieldErrors.durationMinutes").exists());

        mockMvc.perform(post("/api/v1/reservations")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestName\":not-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or unreadable JSON"));
    }

    @Test
    void availabilityValidatesSupportedRanges() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/availability")
                        .with(adminJwt())
                        .queryParam("startAt", Instant.parse("2030-04-12T18:00:00Z").toString())
                        .queryParam("durationMinutes", "5")
                        .queryParam("partySize", "0"))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
            adminJwt() {
        return jwt().jwt(token -> token.subject("7").claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
