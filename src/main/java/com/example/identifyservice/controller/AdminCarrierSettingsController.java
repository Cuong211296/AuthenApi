package com.example.identifyservice.controller;

import com.example.identifyservice.dto.request.ApiResponse;
import com.example.identifyservice.dto.request.CarrierSwitchesRequest;
import com.example.identifyservice.dto.response.ShopSettingsResponse;
import com.example.identifyservice.service.ShopSettingsService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/settings/carriers")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminCarrierSettingsController {
    ShopSettingsService shopSettingsService;

    @PutMapping
    ApiResponse<ShopSettingsResponse> update(@RequestBody @Valid CarrierSwitchesRequest request,
                                             Authentication authentication) {
        return ApiResponse.ok(shopSettingsService.updateCarriers(request, authentication.getName()));
    }
}
