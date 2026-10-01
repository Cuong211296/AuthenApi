package com.example.identifyservice.upload;

import java.util.Map;
import java.util.UUID;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CloudinaryImageStorage implements ImageStorage {
    static final String FOLDER = "quinibear/products";

    private final CloudinaryProperties props;
    private volatile Cloudinary client;

    public CloudinaryImageStorage(CloudinaryProperties props) {
        this.props = props;
    }

    @Override
    public boolean isEnabled() {
        return props.isEnabled();
    }

    @Override
    public String upload(byte[] data, String contentType) {
        if (!props.isEnabled()) {
            throw new ImageStorageException("Cloudinary is not configured");
        }
        try {
            Map<?, ?> result = client().uploader().upload(data, ObjectUtils.asMap(
                    "folder", FOLDER,
                    "public_id", UUID.randomUUID().toString().replace("-", ""),
                    "resource_type", "image",
                    "overwrite", false));
            Object url = result == null ? null : result.get("secure_url");
            if (!(url instanceof String s) || !s.startsWith("https://")) {
                throw new ImageStorageException("Cloudinary returned no secure url");
            }
            return s;
        } catch (ImageStorageException e) {
            throw e;
        } catch (Exception e) {
            // Only the exception class is logged: SDK messages can echo request details.
            log.warn("Cloudinary upload failed: {}", e.getClass().getSimpleName());
            throw new ImageStorageException("Cloudinary upload failed");
        }
    }

    private Cloudinary client() {
        Cloudinary c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    client = new Cloudinary(ObjectUtils.asMap(
                            "cloud_name", props.cloudName().trim(),
                            "api_key", props.apiKey().trim(),
                            "api_secret", props.apiSecret().trim(),
                            "secure", true,
                            "timeout", 15,
                            "connection_timeout", 5));
                }
                c = client;
            }
        }
        return c;
    }
}
