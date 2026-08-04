package com.adam.restaurantoperations.tables;

import java.util.Optional;

import com.adam.restaurantoperations.audit.TableAuditService;
import com.adam.restaurantoperations.auth.service.RequestMetadata;
import com.adam.restaurantoperations.tables.dto.CreateTableRequest;
import com.adam.restaurantoperations.tables.dto.TableActivationRequest;
import com.adam.restaurantoperations.tables.dto.UpdateTableRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RestaurantTableServiceTest {

    private static final RequestMetadata METADATA = new RequestMetadata("127.0.0.1", "test");

    @Mock
    private RestaurantTableRepository repository;

    @Mock
    private TableAuditService auditService;

    private RestaurantTableService service;

    @BeforeEach
    void setUp() {
        service = new RestaurantTableService(repository, auditService);
        lenient().when(repository.saveAndFlush(any(RestaurantTableEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createNormalizesInputAndRecordsAudit() {
        var response = service.create(
                new CreateTableRequest("t-01", " Window ", 4, " Main ", TableStatus.AVAILABLE),
                7L,
                METADATA);

        assertThat(response.tableNumber()).isEqualTo("T-01");
        assertThat(response.displayName()).isEqualTo("Window");
        assertThat(response.section()).isEqualTo("Main");
        verify(auditService).record("TABLE_CREATED", 7L, null, "127.0.0.1");
    }

    @Test
    void duplicateCreateReturnsConflictBeforePersisting() {
        given(repository.existsByTableNumberIgnoreCase("T-01")).willReturn(true);

        assertThatThrownBy(() -> service.create(
                        new CreateTableRequest("T-01", "Window", 4, "Main", TableStatus.AVAILABLE),
                        7L,
                        METADATA))
                .isInstanceOf(TableManagementException.class)
                .hasMessage("Table number is already in use");
    }

    @Test
    void staleUpdateAndActivationAreRejected() {
        var table = new RestaurantTableEntity("T-01", "Window", 4, "Main", TableStatus.AVAILABLE);
        given(repository.findById(12L)).willReturn(Optional.of(table));

        assertThatThrownBy(() -> service.update(
                        12L,
                        new UpdateTableRequest(
                                "T-01", "Updated", 5, "Main", TableStatus.AVAILABLE, 1L),
                        7L,
                        METADATA))
                .isInstanceOf(TableManagementException.class)
                .hasMessageContaining("changed by another request");

        assertThatThrownBy(() -> service.setActivation(
                        12L,
                        new TableActivationRequest(false, 1L),
                        7L,
                        METADATA))
                .isInstanceOf(TableManagementException.class)
                .hasMessageContaining("changed by another request");
    }

    @Test
    void activationUsesSoftStateAndRecordsMatchingAuditAction() {
        var table = new RestaurantTableEntity("T-01", "Window", 4, "Main", TableStatus.AVAILABLE);
        given(repository.findById(12L)).willReturn(Optional.of(table));

        var response = service.setActivation(12L, new TableActivationRequest(false, 0L), 7L, METADATA);

        assertThat(response.active()).isFalse();
        verify(auditService).record("TABLE_DEACTIVATED", 7L, null, "127.0.0.1");
    }
}
