package com.financetracker.controller;

import com.financetracker.dto.ReportDTO;
import com.financetracker.security.SecurityUtils;
import com.financetracker.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/summary")
    public ResponseEntity<ReportDTO> getSummaryReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        return ResponseEntity.ok(reportService.getSummaryReport(userId, startDate, endDate));
    }

    @GetMapping("/income-expense")
    public ResponseEntity<ReportDTO> getIncomeExpenseReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        return ResponseEntity.ok(reportService.getIncomeExpenseReport(userId, startDate, endDate));
    }

    @GetMapping("/category-breakdown")
    public ResponseEntity<ReportDTO> getCategoryBreakdownReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        return ResponseEntity.ok(reportService.getCategoryBreakdownReport(userId, startDate, endDate));
    }

    @GetMapping("/account-balances")
    public ResponseEntity<ReportDTO> getAccountBalanceReport() {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        return ResponseEntity.ok(reportService.getAccountBalanceReport(userId));
    }
}
