package com.example.identifyservice.dto.request;

import jakarta.validation.constraints.Min;

public record ShippingFeeUpdateRequest(@Min(value = 0, message = "INVALID_INPUT") long fee) {
}
