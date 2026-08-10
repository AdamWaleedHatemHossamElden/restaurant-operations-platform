package com.adam.restaurantoperations.kitchen.realtime;

import java.time.Instant;

import com.adam.restaurantoperations.kitchen.KitchenItemStatus;
import com.adam.restaurantoperations.kitchen.KitchenTicketStatus;

public record KitchenRealtimeEvent(
        KitchenEventType eventType,
        Long ticketId,
        Long orderId,
        String orderNumber,
        KitchenTicketStatus ticketStatus,
        Long kitchenItemId,
        KitchenItemStatus kitchenItemStatus,
        Instant timestamp) {
}
