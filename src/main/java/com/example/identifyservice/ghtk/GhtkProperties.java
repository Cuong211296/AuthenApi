package com.example.identifyservice.ghtk;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GHTK shipping-fee settings. Everything may be blank: GHTK is then disabled and the fixed province table is used.
 */
@ConfigurationProperties(prefix = "ghtk")
public record GhtkProperties(String token, String clientSource, String baseUrl, String pickProvince,
                             String pickWard, String pickDistrict, String pickAddress, String transport) {

    /** GHTK is used only when the credentials and the shop pick-up province and ward (from the environment) are set. */
    public boolean isEnabled() {
        return isEnabled(null, null);
    }

    /**
     * Same, but the pick-up province and ward may instead come from the shop settings (both names given): the
     * credentials plus a complete pick-up from either source.
     */
    public boolean isEnabled(String settingsProvince, String settingsWard) {
        return credentialsConfigured() && ((!blank(settingsProvince) && !blank(settingsWard))
                || (!blank(pickProvince) && !blank(pickWard)));
    }

    public boolean credentialsConfigured() {
        return !blank(token) && !blank(clientSource);
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
