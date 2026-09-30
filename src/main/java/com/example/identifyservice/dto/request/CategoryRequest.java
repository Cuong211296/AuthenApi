package com.example.identifyservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "INVALID_INPUT") @Size(max = 100, message = "INVALID_INPUT") String name,
        @NotBlank(message = "INVALID_INPUT") @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "INVALID_INPUT") String slug) {
}
