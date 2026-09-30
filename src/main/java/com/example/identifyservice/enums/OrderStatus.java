package com.example.identifyservice.enums;

public enum OrderStatus {
    PENDING_PAYMENT, PENDING_CONFIRM, CONFIRMED, SHIPPING, COMPLETED, CANCELLED;

    /** Manual (admin) transitions. PENDING_PAYMENT -> PENDING_CONFIRM happens only through payment. */
    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDING_PAYMENT -> next == CANCELLED;
            case PENDING_CONFIRM -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == SHIPPING || next == CANCELLED;
            case SHIPPING -> next == COMPLETED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
