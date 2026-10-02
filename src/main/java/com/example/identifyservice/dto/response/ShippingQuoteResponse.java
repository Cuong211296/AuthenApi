package com.example.identifyservice.dto.response;

import com.example.identifyservice.enums.ShippingSource;
import com.example.identifyservice.service.ShippingQuoteService.QuoteOptions;

import java.util.List;

/**
 * The top-level fields describe the default selection (the cheapest live carrier, or the table fallback);
 * {@code options} lists every live carrier cheapest first (never TABLE, empty on a fallback).
 */
public record ShippingQuoteResponse(long fee, ShippingSource source, boolean estimated, int weightGrams,
                                    boolean deliverable, String message, List<Option> options) {
    public record Option(ShippingSource source, long fee, boolean estimated) {
    }

    public static ShippingQuoteResponse from(QuoteOptions result) {
        ShippingQuote q = result.selected();
        List<Option> options = result.options().stream()
                .map(o -> new Option(o.source(), o.fee(), o.estimated())).toList();
        return new ShippingQuoteResponse(q.fee(), q.source(), q.estimated(), q.weightGrams(), q.deliverable(),
                q.message(), options);
    }
}
