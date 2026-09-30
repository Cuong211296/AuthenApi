package com.example.identifyservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VariantRequest(
        @NotBlank(message = "INVALID_INPUT") @Size(max = 20, message = "INVALID_INPUT") String size,
        @NotBlank(message = "INVALID_INPUT") @Size(max = 50, message = "INVALID_INPUT") String color,
        @NotBlank(message = "INVALID_INPUT") @Size(max = 64, message = "INVALID_INPUT") String sku,
        @Min(value = 0, message = "INVALID_INPUT") int stock,
        @Min(value = 0, message = "INVALID_INPUT") Long price,
        Boolean active) {
}
