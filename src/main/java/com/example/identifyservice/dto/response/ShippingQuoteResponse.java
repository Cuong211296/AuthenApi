package com.example.identifyservice.dto.response;

import com.example.identifyservice.enums.ShippingSource;

public record ShippingQuoteResponse(long fee, ShippingSource source, boolean estimated, int weightGrams,
                                    boolean deliverable, String message) {
    public static ShippingQuoteResponse from(ShippingQuote q) {
        return new ShippingQuoteResponse(q.fee(), q.source(), q.estimated(), q.weightGrams(), q.deliverable(),
                q.message());
    }
}
