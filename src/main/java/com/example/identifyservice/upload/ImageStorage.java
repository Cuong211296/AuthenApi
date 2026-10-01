package com.example.identifyservice.upload;

public interface ImageStorage {
    /** True when the storage is configured and usable. */
    boolean isEnabled();

    /**
     * Stores an already validated image and returns its public https URL.
     *
     * @throws ImageStorageException when the storage cannot be reached or answers something unusable
     */
    String upload(byte[] data, String contentType);
}
