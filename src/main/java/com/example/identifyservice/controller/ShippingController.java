package com.example.identifyservice.controller;

import com.example.identifyservice.dto.request.ApiResponse;
import com.example.identifyservice.dto.request.ShippingFeeUpdateRequest;
import com.example.identifyservice.dto.request.ShippingQuoteRequest;
import com.example.identifyservice.dto.request.ShippingRateRequest;
import com.example.identifyservice.dto.response.CodeNameResponse;
import com.example.identifyservice.dto.response.IdNameResponse;
import com.example.identifyservice.dto.response.ShippingFeeResponse;
import com.example.identifyservice.dto.response.ShippingQuoteResponse;
import com.example.identifyservice.dto.response.ShippingRateResponse;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.ghn.GhnMasterDataService;
import com.example.identifyservice.ghn.GhnUnavailableException;
import com.example.identifyservice.service.ShippingQuoteService;
import com.example.identifyservice.service.ShippingService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.Supplier;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ShippingController {
    ShippingService shippingService;
    ShippingQuoteService shippingQuoteService;
    GhnMasterDataService ghnMasterDataService;

    /** Authenticated: quotes the shipping fee of the signed-in user's current cart. */
    @PostMapping("/shipping/quote")
    ApiResponse<ShippingQuoteResponse> quote(@RequestBody @Valid ShippingQuoteRequest request) {
        return ApiResponse.ok(ShippingQuoteResponse.from(
                shippingQuoteService.quoteOptionsForCurrentCart(request.toAddress())));
    }

    /** Public: which carrier serves quotes and whether the checkout uses GHN id selects or text address fields. */
    @GetMapping("/shipping/config")
    ApiResponse<ShippingQuoteService.ProviderConfig> config() {
        return ApiResponse.ok(shippingQuoteService.providerConfig());
    }

    @GetMapping("/shipping/ghn/provinces")
    ApiResponse<List<IdNameResponse>> ghnProvinces() {
        return ApiResponse.ok(masterData(() -> ghnMasterDataService.provinces().stream()
                .map(p -> new IdNameResponse(p.id(), p.name())).toList()));
    }

    @GetMapping("/shipping/ghn/districts")
    ApiResponse<List<IdNameResponse>> ghnDistricts(@RequestParam int provinceId) {
        return ApiResponse.ok(masterData(() -> ghnMasterDataService.districts(provinceId).stream()
                .map(d -> new IdNameResponse(d.id(), d.name())).toList()));
    }

    /** The province id is required so only districts that exist in that province can reach GHN. */
    @GetMapping("/shipping/ghn/wards")
    ApiResponse<List<CodeNameResponse>> ghnWards(@RequestParam int provinceId, @RequestParam int districtId) {
        return ApiResponse.ok(masterData(() -> ghnMasterDataService.wards(provinceId, districtId)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_INPUT)).stream()
                .map(w -> new CodeNameResponse(w.code(), w.name())).toList()));
    }

    /** GHN disabled or down: 502 SHIPPING_PROVIDER_UNAVAILABLE, the UI then falls back to the text address. */
    private static <T> T masterData(Supplier<T> call) {
        try {
            return call.get();
        } catch (GhnUnavailableException e) {
            throw new AppException(ErrorCode.SHIPPING_PROVIDER_UNAVAILABLE);
        }
    }

    @GetMapping("/shipping/fee")
    ApiResponse<ShippingFeeResponse> fee(@RequestParam String province) {
        return ApiResponse.ok(shippingService.fee(province));
    }

    @GetMapping("/shipping/provinces")
    ApiResponse<List<ShippingRateResponse>> provinces() {
        return ApiResponse.ok(shippingService.list());
    }

    @GetMapping("/admin/shipping-rates")
    ApiResponse<List<ShippingRateResponse>> adminList() {
        return ApiResponse.ok(shippingService.list());
    }

    @PostMapping("/admin/shipping-rates")
    ApiResponse<ShippingRateResponse> adminCreate(@RequestBody @Valid ShippingRateRequest request) {
        return ApiResponse.ok(shippingService.create(request));
    }

    @PutMapping("/admin/shipping-rates/{id}")
    ApiResponse<ShippingRateResponse> adminUpdate(@PathVariable String id, @RequestBody @Valid ShippingFeeUpdateRequest request) {
        return ApiResponse.ok(shippingService.updateFee(id, request.fee()));
    }
}
