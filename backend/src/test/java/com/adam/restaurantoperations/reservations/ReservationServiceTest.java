package com.adam.restaurantoperations.reservations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.adam.restaurantoperations.audit.ReservationAuditService;
import com.adam.restaurantoperations.auth.service.RequestMetadata;
import com.adam.restaurantoperations.reservations.dto.CreateReservationRequest;
import com.adam.restaurantoperations.reservations.dto.ReservationStatusRequest;
import com.adam.restaurantoperations.tables.RestaurantTableEntity;
import com.adam.restaurantoperations.tables.RestaurantTableRepository;
import com.adam.restaurantoperations.tables.TableStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Instant START = Instant.parse("2030-04-12T18:00:00Z");
    private static final RequestMetadata METADATA = new RequestMetadata("127.0.0.1", "test");

    @Mock
    private ReservationRepository repository;

    @Mock
    private RestaurantTableRepository tableRepository;

    @Mock
    private ReservationCodeGenerator codeGenerator;

    @Mock
    private ReservationAuditService auditService;

    private ReservationService service;

    @BeforeEach
    void setUp() {
        service = new ReservationService(repository, tableRepository, codeGenerator, auditService);
        lenient().when(repository.saveAndFlush(any(ReservationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(codeGenerator.generate()).thenReturn("RSV-TESTCODE123");
    }

    @Test
    void createsUnassignedPendingReservationAndAuditsSafeIdentifier() {
        var response = service.create(createRequest(null, 4), 7L, METADATA);

        assertThat(response.reservationCode()).isEqualTo("RSV-TESTCODE123");
        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(response.restaurantTable()).isNull();
        verify(auditService).record("RESERVATION_CREATED", 7L, null, null, "127.0.0.1");
    }

    @Test
    void rejectsCapacityAndBlockingOverlapForAssignedReservation() {
        var table = new RestaurantTableEntity("T-01", "Window", 2, "Main", TableStatus.AVAILABLE);
        given(tableRepository.findByIdForReservationUpdate(12L)).willReturn(Optional.of(table));

        assertThatThrownBy(() -> service.create(createRequest(12L, 4), 7L, METADATA))
                .isInstanceOf(ReservationManagementException.class)
                .hasMessage("Restaurant table capacity is insufficient");

        table = new RestaurantTableEntity("T-01", "Window", 6, "Main", TableStatus.AVAILABLE);
        given(tableRepository.findByIdForReservationUpdate(12L)).willReturn(Optional.of(table));
        given(repository.findBlockingOverlapIdsForUpdate(12L, START, START.plusSeconds(5400), null))
                .willReturn(List.of(99L));

        assertThatThrownBy(() -> service.create(createRequest(12L, 4), 7L, METADATA))
                .isInstanceOf(ReservationManagementException.class)
                .hasMessageContaining("unavailable for the requested time");
    }

    @Test
    void enforcesStatusTransitionsAndAvailabilityOnConfirmation() {
        var reservation = new ReservationEntity(
                "RSV-TESTCODE123", "Guest", "+12025550123", null, 4, START, 90, null, null);
        given(repository.findById(9L)).willReturn(Optional.of(reservation));

        var confirmed = service.transition(
                9L, new ReservationStatusRequest(ReservationStatus.CONFIRMED, 0L), 7L, METADATA);
        assertThat(confirmed.status()).isEqualTo(ReservationStatus.CONFIRMED);

        assertThatThrownBy(() -> service.transition(
                        9L,
                        new ReservationStatusRequest(ReservationStatus.COMPLETED, 0L),
                        7L,
                        METADATA))
                .isInstanceOf(ReservationManagementException.class)
                .hasMessageContaining("transition is not allowed");
    }

    private CreateReservationRequest createRequest(Long tableId, int partySize) {
        return new CreateReservationRequest(
                " Guest ", "+12025550123", "guest@example.com", partySize, START, 90, tableId, null);
    }
}
