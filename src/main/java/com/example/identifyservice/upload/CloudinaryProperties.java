package com.example.identifyservice.upload;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cloudinary settings. Image upload is enabled only when cloud name, API key and API secret are all non-blank; blank
 * configuration never blocks startup (the upload endpoint then answers 503). Never log this record's values.
 */
@ConfigurationProperties(prefix = "cloudinary")
public record CloudinaryProperties(String cloudName, String apiKey, String apiSecret) {
    public boolean isEnabled() {
        return !blank(cloudName) && !blank(apiKey) && !blank(apiSecret);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public String toString() {
        return "CloudinaryProperties[enabled=" + isEnabled() + "]";
    }
}
