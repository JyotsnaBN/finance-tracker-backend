package com.financetracker.controller;

import com.financetracker.dto.ReportDTO;
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
            @RequestParam UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        return ResponseEntity.ok(
                reportService.getSummaryReport(userId, startDate, endDate)
        );
    }

    @GetMapping("/income-expense")
    public ResponseEntity<ReportDTO> getIncomeExpenseReport(
            @RequestParam UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        return ResponseEntity.ok(
                reportService.getIncomeExpenseReport(userId, startDate, endDate)
        );
    }

    @GetMapping("/category-breakdown")
    public ResponseEntity<ReportDTO> getCategoryBreakdownReport(
            @RequestParam UUID userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        return ResponseEntity.ok(
                reportService.getCategoryBreakdownReport(userId, startDate, endDate)
        );
    }

    @GetMapping("/account-balances")
    public ResponseEntity<ReportDTO> getAccountBalanceReport(
            @RequestParam UUID userId) {

        return ResponseEntity.ok(
                reportService.getAccountBalanceReport(userId)
        );
    }
}
