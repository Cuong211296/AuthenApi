package com.example.identifyservice.dto.response;

import com.example.identifyservice.entity.Category;

public record CategoryResponse(String id, String name, String slug) {
    public static CategoryResponse from(Category c) {
        return c == null ? null : new CategoryResponse(c.getId(), c.getName(), c.getSlug());
    }
}
