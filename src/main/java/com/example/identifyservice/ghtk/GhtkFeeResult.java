package com.example.identifyservice.ghtk;

/**
 * Parsed GHTK fee answer. {@code success=false} means GHTK rejected the query; {@code deliverable=false} means GHTK
 * does not deliver to the address. {@code fee} excludes the insurance fee.
 */
public record GhtkFeeResult(boolean success, boolean deliverable, long fee, String message) {
}
