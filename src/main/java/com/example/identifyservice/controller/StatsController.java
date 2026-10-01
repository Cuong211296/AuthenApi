package com.example.identifyservice.controller;

import com.example.identifyservice.dto.request.ApiResponse;
import com.example.identifyservice.dto.response.stats.StatsOverviewResponse;
import com.example.identifyservice.service.StatsService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/stats")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StatsController {
    StatsService statsService;

    /** Dates arrive as raw strings so malformed values map to INVALID_INPUT (400) instead of a binding error. */
    @GetMapping("/overview")
    ApiResponse<StatsOverviewResponse> overview(@RequestParam(required = false) String from,
                                                @RequestParam(required = false) String to,
                                                @RequestParam(required = false) String groupBy) {
        return ApiResponse.ok(statsService.overview(from, to, groupBy));
    }
}
