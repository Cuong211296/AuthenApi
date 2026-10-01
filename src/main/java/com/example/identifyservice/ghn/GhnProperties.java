package com.example.identifyservice.ghn;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GHN (Giao Hang Nhanh) settings. GHN is enabled only when the token and the shop id are both non-blank; blank
 * configuration never blocks startup (the GHTK / table fallback chain is used instead).
 */
@ConfigurationProperties(prefix = "ghn")
public record GhnProperties(String token, String shopId, String baseUrl, Integer fromDistrictId,
                            Integer serviceTypeId, Integer defaultLength, Integer defaultWidth,
                            Integer defaultHeight) {
    public static final String DEFAULT_BASE_URL = "https://dev-online-gateway.ghn.vn";

    public boolean isEnabled() {
        return !blank(token) && !blank(shopId);
    }

    public String effectiveBaseUrl() {
        return blank(baseUrl) ? DEFAULT_BASE_URL : baseUrl;
    }

    /** 2 = GHN standard e-commerce delivery. */
    public int serviceType() {
        return serviceTypeId == null ? 2 : serviceTypeId;
    }

    public int length() {
        return defaultLength == null ? 25 : defaultLength;
    }

    public int width() {
        return defaultWidth == null ? 20 : defaultWidth;
    }

    public int height() {
        return defaultHeight == null ? 10 : defaultHeight;
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
