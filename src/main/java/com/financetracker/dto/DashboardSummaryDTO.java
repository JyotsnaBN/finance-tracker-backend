package com.financetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDTO {
    private MonthSummary currentMonth;
    private MonthSummary lastMonth;
    private Comparison comparison;
    private List<CategorySummary> topCategories;
    private List<RecentTransaction> recentTransactions;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthSummary {
        private BigDecimal income;
        private BigDecimal expenses;
        private BigDecimal savings;
        private BigDecimal savingsRate;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Comparison {
        private BigDecimal incomeChange;
        private BigDecimal expenseChange;
        private BigDecimal savingsChange;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategorySummary {
        private String categoryName;
        private BigDecimal amount;
        private BigDecimal percentage;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentTransaction {
        private Long id;
        private String description;
        private BigDecimal amount;
        private String type;
        private String categoryName;
        private Instant date;
    }
}
