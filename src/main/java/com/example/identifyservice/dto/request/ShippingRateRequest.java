package com.example.identifyservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShippingRateRequest(
        @NotBlank(message = "INVALID_INPUT") @Size(max = 100, message = "INVALID_INPUT") String province,
        @Min(value = 0, message = "INVALID_INPUT") long fee) {
}
