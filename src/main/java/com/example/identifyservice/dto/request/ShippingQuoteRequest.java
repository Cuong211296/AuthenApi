package com.example.identifyservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShippingQuoteRequest(
        @NotBlank(message = "INVALID_INPUT") String province,
        @NotBlank(message = "INVALID_INPUT") @Size(max = 100, message = "INVALID_INPUT") String ward,
        @Size(max = 300, message = "INVALID_INPUT") String address) {
}
