package com.example.identifyservice.upload;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.identifyservice.dto.request.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/uploads")
@RequiredArgsConstructor
public class AdminUploadController {
    private final ImageUploadService imageUploadService;

    @PostMapping("/image")
    @PreAuthorize("hasRole('ADMIN')")
    ApiResponse<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(Map.of("url", imageUploadService.upload(file)));
    }
}
