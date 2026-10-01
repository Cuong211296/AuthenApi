package com.example.identifyservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProductRequest(
        @NotBlank(message = "INVALID_INPUT") @Size(max = 200, message = "INVALID_INPUT") String name,
        @NotBlank(message = "INVALID_INPUT") @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "INVALID_INPUT") String slug,
        @Size(max = 4000, message = "INVALID_INPUT") String description,
        String categoryId,
        @Min(value = 0, message = "INVALID_INPUT") long basePrice,
        @Min(value = 0, message = "INVALID_INPUT") Long costPrice,
        @Size(max = 500, message = "INVALID_INPUT") String imageUrl,
        Boolean active) {
}
