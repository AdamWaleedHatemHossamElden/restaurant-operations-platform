package com.adam.restaurantoperations.kitchen.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.adam.restaurantoperations.kitchen.KitchenItemStatus;
import com.adam.restaurantoperations.kitchen.KitchenTicketStatus;

class KitchenRealtimeBroadcasterTest {
    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final KitchenRealtimeBroadcaster broadcaster = new KitchenRealtimeBroadcaster(messaging);

    @Test
    void broadcastsOnlyThroughAnAfterCommitListener() throws NoSuchMethodException {
        Method method = KitchenRealtimeBroadcaster.class.getMethod(
                "broadcast", KitchenRealtimeEvent.class);
        TransactionalEventListener listener = method.getAnnotation(TransactionalEventListener.class);

        assertThat(listener).isNotNull();
        assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);

        KitchenRealtimeEvent event = event();
        broadcaster.broadcast(event);

        verify(messaging).convertAndSend(KitchenRealtimeBroadcaster.KITCHEN_TOPIC, event);
    }

    @Test
    void deliveryFailureCannotRollBackOrSurfaceAfterTheDatabaseCommit() {
        KitchenRealtimeEvent event = event();
        doThrow(new IllegalStateException("delivery unavailable"))
                .when(messaging)
                .convertAndSend(KitchenRealtimeBroadcaster.KITCHEN_TOPIC, event);

        assertThatCode(() -> broadcaster.broadcast(event)).doesNotThrowAnyException();
    }

    private KitchenRealtimeEvent event() {
        return new KitchenRealtimeEvent(
                KitchenEventType.KITCHEN_ITEM_STATUS_CHANGED,
                1L,
                2L,
                "ORD-1",
                KitchenTicketStatus.PREPARING,
                3L,
                KitchenItemStatus.PREPARING,
                Instant.parse("2030-01-01T10:00:00Z"));
    }
}
