package com.example.identifyservice.dto.response;

import com.example.identifyservice.enums.ShippingSource;

/**
 * Result of a shipping quote: {@code estimated} is true when the fee comes from the fixed table instead of GHTK;
 * {@code deliverable} is false only when GHTK refuses the address and the table has no entry either (fee is then 0);
 * {@code message} is a Vietnamese note shown to the customer when a fallback happened (null otherwise).
 */
public record ShippingQuote(long fee, ShippingSource source, boolean estimated, int weightGrams,
                            boolean deliverable, String message) {
}
