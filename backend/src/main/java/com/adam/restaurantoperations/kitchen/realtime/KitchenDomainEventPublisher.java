package com.adam.restaurantoperations.kitchen.realtime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class KitchenDomainEventPublisher {
    private final ApplicationEventPublisher events;

    public KitchenDomainEventPublisher(ApplicationEventPublisher events) {
        this.events = events;
    }

    public void publish(KitchenRealtimeEvent event) {
        events.publishEvent(event);
    }
}
