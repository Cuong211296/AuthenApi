package com.example.identifyservice.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Circuit breaker of one shipping carrier: after a failure the carrier is skipped for {@link #OPEN_FOR}; then a
 * single half-open probe call is allowed. All methods are short and synchronized and never run the carrier call
 * themselves, so no lock is held during HTTP. The caller must release a PROBE permit in a {@code finally}, so a probe
 * that throws an Error cannot leave the breaker stuck.
 */
final class CarrierBreaker {
    static final Duration OPEN_FOR = Duration.ofSeconds(60);

    enum Permit {DENIED, NORMAL, PROBE}

    private final Clock clock;
    private Instant openUntil;
    private boolean probing;

    CarrierBreaker(Clock clock) {
        this.clock = clock;
    }

    synchronized Permit acquire() {
        if (openUntil == null) return Permit.NORMAL;
        if (clock.instant().isBefore(openUntil)) return Permit.DENIED;
        if (probing) return Permit.DENIED;
        probing = true;
        return Permit.PROBE;
    }

    /** A clean answer (even a refusal) means the carrier is reachable. */
    synchronized void success() {
        openUntil = null;
        probing = false;
    }

    synchronized void failure() {
        openUntil = clock.instant().plus(OPEN_FOR);
        probing = false;
    }

    /** Always called from a finally block; only a PROBE permit owns the probing flag. */
    synchronized void release(Permit permit) {
        if (permit == Permit.PROBE) probing = false;
    }

    synchronized void reset() {
        openUntil = null;
        probing = false;
    }
}
