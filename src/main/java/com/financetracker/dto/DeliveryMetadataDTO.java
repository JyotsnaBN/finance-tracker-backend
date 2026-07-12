package com.financetracker.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryMetadataDTO {
    private List<DeliveryEventDTO> deliveries = new ArrayList<>();
    
    public void addDelivery(DeliveryEventDTO event) {
        if (event.getTrackingNumber() != null && 
            deliveries.stream().noneMatch(d -> 
                event.getTrackingNumber().equals(d.getTrackingNumber()))) {
            deliveries.add(event);
        }
    }
    
    public int getTotalItemCount() {
        return deliveries.stream()
            .mapToInt(d -> d.getItemCount() != null ? d.getItemCount() : 0)
            .sum();
    }
}
