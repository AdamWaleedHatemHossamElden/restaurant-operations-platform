package com.adam.restaurantoperations.kitchen.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class KitchenRealtimeBroadcaster {
    public static final String KITCHEN_TOPIC = "/topic/kitchen";

    private static final Logger LOGGER = LoggerFactory.getLogger(KitchenRealtimeBroadcaster.class);

    private final SimpMessagingTemplate messaging;

    public KitchenRealtimeBroadcaster(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void broadcast(KitchenRealtimeEvent event) {
        try {
            messaging.convertAndSend(KITCHEN_TOPIC, event);
        } catch (RuntimeException exception) {
            LOGGER.warn("Kitchen notification delivery failed after commit for ticket {}", event.ticketId());
        }
    }
}
