package com.financetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceHistoryDTO {
    private UUID accountId;
    private String accountName;
    private String interval;
    private List<BalanceDataPoint> data;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceDataPoint {
        private LocalDate date;
        private BigDecimal balance;
        private BigDecimal availableLimit;
        private Boolean hasActualLimit;   
    }
}
