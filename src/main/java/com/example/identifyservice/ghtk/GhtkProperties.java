package com.example.identifyservice.ghtk;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GHTK shipping-fee settings. Everything may be blank: GHTK is then disabled and the fixed province table is used.
 */
@ConfigurationProperties(prefix = "ghtk")
public record GhtkProperties(String token, String clientSource, String baseUrl, String pickProvince,
                             String pickWard, String pickDistrict, String pickAddress, String transport) {

    /** GHTK is used only when the credentials and the shop pick-up province and ward are all set. */
    public boolean isEnabled() {
        return !blank(token) && !blank(clientSource) && !blank(pickProvince) && !blank(pickWard);
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
