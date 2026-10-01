package com.example.identifyservice.upload;

import java.io.IOException;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageUploadService {
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    /** Product.imageUrl is limited to 500 chars; Cloudinary URLs are about 110. */
    static final int MAX_URL_LENGTH = 500;

    private final ImageStorage storage;

    public String upload(MultipartFile file) {
        if (!storage.isEnabled()) {
            throw new AppException(ErrorCode.IMAGE_UPLOAD_DISABLED);
        }
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }
        if (file.getSize() > MAX_BYTES) {
            throw new AppException(ErrorCode.IMAGE_TOO_LARGE);
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new AppException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
        return upload(data);
    }

    /** Validates size and magic bytes before touching the storage; client filename and Content-Type are ignored. */
    String upload(byte[] data) {
        if (!storage.isEnabled()) {
            throw new AppException(ErrorCode.IMAGE_UPLOAD_DISABLED);
        }
        if (data == null || data.length == 0) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }
        if (data.length > MAX_BYTES) {
            throw new AppException(ErrorCode.IMAGE_TOO_LARGE);
        }
        String type = detectType(data);
        if (type == null) {
            throw new AppException(ErrorCode.IMAGE_INVALID_TYPE);
        }
        String url;
        try {
            url = storage.upload(data, type);
        } catch (RuntimeException e) {
            log.warn("Image upload failed: {}", e.getClass().getSimpleName());
            throw new AppException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
        if (url == null || !url.startsWith("https://") || url.length() > MAX_URL_LENGTH) {
            throw new AppException(ErrorCode.IMAGE_UPLOAD_FAILED);
        }
        return url;
    }

    /** @return image/jpeg, image/png or image/webp, or null when the bytes are none of them */
    static String detectType(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G'
                && d[4] == 0x0D && d[5] == 0x0A && d[6] == 0x1A && d[7] == 0x0A) {
            return "image/png";
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
