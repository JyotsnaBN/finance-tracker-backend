package com.financetracker.controller;

import com.financetracker.dto.DashboardSummaryDTO;
import com.financetracker.dto.SpendingTrendsDTO;
import com.financetracker.security.SecurityUtils;
import com.financetracker.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {
    private final AnalyticsService analyticsService;

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardSummaryDTO> getDashboardSummary() {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        return ResponseEntity.ok(analyticsService.getDashboardSummary(userId));
    }

    @GetMapping("/trends")
    public ResponseEntity<SpendingTrendsDTO> getSpendingTrends(
            @RequestParam String period,
            @RequestParam(required = false) UUID accountId) {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        return ResponseEntity.ok(analyticsService.getSpendingTrends(userId, period, accountId));
    }
}
