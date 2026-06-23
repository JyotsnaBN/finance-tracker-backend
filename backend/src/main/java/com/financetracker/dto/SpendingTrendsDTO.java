package com.financetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpendingTrendsDTO {
    private String period;
    private List<TrendDataPoint> data;
    private Averages averages;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendDataPoint {
        private String label;
        private BigDecimal income;
        private BigDecimal expenses;
        private BigDecimal savings;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Averages {
        private BigDecimal income;
        private BigDecimal expenses;
        private BigDecimal savings;
    }
}
