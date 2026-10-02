package com.example.identifyservice.dto.request;

import jakarta.validation.constraints.NotNull;

/** Both switches are always sent, so a partial body can never silently flip the other carrier. */
public record CarrierSwitchesRequest(
        @NotNull(message = "INVALID_INPUT") Boolean ghn,
        @NotNull(message = "INVALID_INPUT") Boolean ghtk) {
}
