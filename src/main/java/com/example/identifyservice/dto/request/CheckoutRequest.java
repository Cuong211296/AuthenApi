package com.example.identifyservice.dto.request;

import com.example.identifyservice.enums.PaymentMethod;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CheckoutRequest(
        @NotBlank(message = "INVALID_INPUT") @Size(max = 100, message = "INVALID_INPUT") String receiverName,
        @NotBlank(message = "INVALID_INPUT") @Pattern(regexp = "^(0|\\+84)[0-9]{9}$", message = "INVALID_INPUT") String phone,
        @NotBlank(message = "INVALID_INPUT") @Email(message = "INVALID_INPUT") @Size(max = 150, message = "INVALID_INPUT") String email,
        @NotBlank(message = "INVALID_INPUT") @Size(max = 300, message = "INVALID_INPUT") String address,
        @NotBlank(message = "INVALID_INPUT") String province,
        @Size(max = 500, message = "INVALID_INPUT") String note,
        @NotNull(message = "INVALID_INPUT") PaymentMethod paymentMethod) {
}
