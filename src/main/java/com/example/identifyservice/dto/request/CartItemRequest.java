package com.example.identifyservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CartItemRequest(@NotBlank(message = "INVALID_INPUT") String variantId, int quantity) {
}
