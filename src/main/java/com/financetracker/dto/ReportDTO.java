package com.financetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportDTO {
    private LocalDate startDate;
    private LocalDate endDate;
    private String reportType;
    
    private BigDecimal totalIncome;
    private BigDecimal totalExpenses;
    private BigDecimal netSavings;
    
    private Map<String, BigDecimal> expensesByCategory;
    private Map<String, BigDecimal> incomeByCategory;
    
    private List<DailyTrendDTO> dailyTrends;
    
    private List<CategorySummaryDTO> topExpenseCategories;
    
    private Map<String, BigDecimal> balanceByAccount;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyTrendDTO {
        private LocalDate date;
        private BigDecimal income;
        private BigDecimal expenses;
        private BigDecimal net;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategorySummaryDTO {
        private String categoryName;
        private BigDecimal amount;
        private Integer transactionCount;
        private Double percentage;
    }
}
