package com.example.identifyservice.controller;

import com.example.identifyservice.dto.request.ApiResponse;
import com.example.identifyservice.dto.request.ShopSettingsRequest;
import com.example.identifyservice.dto.response.ShopSettingsResponse;
import com.example.identifyservice.service.ShopSettingsService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/settings/shop")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminShopSettingsController {
    ShopSettingsService shopSettingsService;

    @GetMapping
    ApiResponse<ShopSettingsResponse> get() {
        return ApiResponse.ok(shopSettingsService.get());
    }

    @PutMapping
    ApiResponse<ShopSettingsResponse> update(@RequestBody ShopSettingsRequest request, Authentication authentication) {
        return ApiResponse.ok(shopSettingsService.update(request, authentication.getName()));
    }
}
