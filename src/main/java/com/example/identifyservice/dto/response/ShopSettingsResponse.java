package com.example.identifyservice.dto.response;

import com.example.identifyservice.service.PickupAddress;

import java.time.Instant;

/** Shop settings for the admin UI. The GHN shop id is shown (not a secret); tokens are never part of any response. */
public record ShopSettingsResponse(String shopName, String phone, PickupAddress pickup, Carriers carriers,
                                   String addressMode, Instant updatedAt, String updatedBy) {
    public record Carriers(Ghn ghn, Ghtk ghtk) {
    }

    public record Ghn(boolean enabled, String shopId) {
    }

    public record Ghtk(boolean enabled) {
    }
}
