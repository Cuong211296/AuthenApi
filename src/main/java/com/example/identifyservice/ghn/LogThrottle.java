package com.example.identifyservice.ghn;

import java.time.Duration;

/** Lets a repeating log line through at most once per interval. */
final class LogThrottle {
    private final long intervalMillis;
    private boolean any;
    private long last;

    LogThrottle(Duration interval) {
        this.intervalMillis = interval.toMillis();
    }

    synchronized boolean allow(long nowMillis) {
        if (any && nowMillis - last < intervalMillis) return false;
        any = true;
        last = nowMillis;
        return true;
    }
}
