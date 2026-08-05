package com.adam.restaurantoperations.reservations;

import java.util.Set;

public enum ReservationStatus {
    PENDING,
    CONFIRMED,
    SEATED,
    COMPLETED,
    CANCELLED,
    NO_SHOW;

    private static final Set<ReservationStatus> TERMINAL = Set.of(COMPLETED, CANCELLED, NO_SHOW);
    private static final Set<ReservationStatus> BLOCKING = Set.of(CONFIRMED, SEATED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean blocksAvailability() {
        return BLOCKING.contains(this);
    }

    public boolean canTransitionTo(ReservationStatus target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == SEATED || target == CANCELLED || target == NO_SHOW;
            case SEATED -> target == COMPLETED || target == CANCELLED;
            case COMPLETED, CANCELLED, NO_SHOW -> false;
        };
    }
}
