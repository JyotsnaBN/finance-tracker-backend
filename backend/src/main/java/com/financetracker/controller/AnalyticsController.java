package com.financetracker.controller;

import com.financetracker.dto.DashboardSummaryDTO;
import com.financetracker.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.financetracker.dto.SpendingTrendsDTO;

import java.util.UUID;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {
    private final AnalyticsService analyticsService;
    
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardSummaryDTO> getDashboardSummary(
            @RequestParam UUID userId) {
        return ResponseEntity.ok(analyticsService.getDashboardSummary(userId));
    }

    @GetMapping("/trends")
    public ResponseEntity<SpendingTrendsDTO> getSpendingTrends(
            @RequestParam UUID userId,
            @RequestParam String period,
            @RequestParam(required = false) UUID accountId) {
        return ResponseEntity.ok(analyticsService.getSpendingTrends(userId, period, accountId));
    }

}
