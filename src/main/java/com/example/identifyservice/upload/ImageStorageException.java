package com.example.identifyservice.upload;

/** Unchecked failure of the image storage. The message never contains credentials or file content. */
public class ImageStorageException extends RuntimeException {
    public ImageStorageException(String message) {
        super(message);
    }
}
