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
public class DeliveryEventDTO {
    private String trackingNumber;
    private String merchant;
    private String storeName;
    private Instant deliveryDate;
    private String deliveryDuration;
    private Integer itemCount;
    private List<OrderItemDTO> items;
    private BigDecimal savingsAmount;
    private Instant emailProcessedAt;
}
