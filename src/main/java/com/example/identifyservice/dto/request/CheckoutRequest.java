package com.example.identifyservice.dto.request;

import com.example.identifyservice.enums.PaymentMethod;
import com.example.identifyservice.service.QuoteAddress;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Address: either the GHN ids ({@code provinceId}, {@code districtId}, {@code wardCode}; the server stores the names
 * from GHN master data, never the client's) or the text {@code province} + {@code ward}. The cross-field rule is
 * enforced by the checkout service (INVALID_INPUT).
 */
public record CheckoutRequest(
        @NotBlank(message = "INVALID_INPUT") @Size(max = 100, message = "INVALID_INPUT") String receiverName,
        @NotBlank(message = "INVALID_INPUT") @Pattern(regexp = "^(0|\\+84)[0-9]{9}$", message = "INVALID_INPUT") String phone,
        @NotBlank(message = "INVALID_INPUT") @Email(message = "INVALID_INPUT") @Size(max = 150, message = "INVALID_INPUT") String email,
        @NotBlank(message = "INVALID_INPUT") @Size(max = 300, message = "INVALID_INPUT") String address,
        @Size(max = 100, message = "INVALID_INPUT") String province,
        @Size(max = 100, message = "INVALID_INPUT") String ward,
        @Size(max = 500, message = "INVALID_INPUT") String note,
        @NotNull(message = "INVALID_INPUT") PaymentMethod paymentMethod,
        @Positive(message = "INVALID_INPUT") Integer provinceId,
        @Positive(message = "INVALID_INPUT") Integer districtId,
        @Size(max = 20, message = "INVALID_INPUT") String wardCode,
        @Size(max = 100, message = "INVALID_INPUT") String district) {

    /** Text-address request (no GHN ids). */
    public CheckoutRequest(String receiverName, String phone, String email, String address, String province,
                           String ward, String note, PaymentMethod paymentMethod) {
        this(receiverName, phone, email, address, province, ward, note, paymentMethod, null, null, null, null);
    }

    public QuoteAddress toAddress() {
        return new QuoteAddress(province, district, ward, address, provinceId, districtId, wardCode);
    }
}
