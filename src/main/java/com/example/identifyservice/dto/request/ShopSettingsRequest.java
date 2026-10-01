package com.example.identifyservice.dto.request;

import com.example.identifyservice.service.PickupAddress;

/** Body of {@code PUT /admin/settings/shop}; the rules are enforced by the settings service (INVALID_INPUT). */
public record ShopSettingsRequest(String shopName, String phone, PickupAddress pickup) {
}
