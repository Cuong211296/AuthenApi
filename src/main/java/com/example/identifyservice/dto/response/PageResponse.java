package com.example.identifyservice.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, int totalPages, long totalElements) {
    public static <T> PageResponse<T> of(Page<T> p) {
        return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalPages(), p.getTotalElements());
    }
}
