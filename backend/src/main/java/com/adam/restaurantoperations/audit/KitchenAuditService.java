package com.adam.restaurantoperations.audit;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class KitchenAuditService {
    private final AuditLogRepository repository;

    public KitchenAuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(
            String action,
            Long userId,
            Long ticketId,
            Long orderId,
            Long kitchenItemId,
            String ipAddress) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("orderId", orderId);
        if (kitchenItemId != null) {
            details.put("kitchenItemId", kitchenItemId);
        }
        repository.save(new AuditLogEntity(
                userId,
                action,
                "KITCHEN_TICKET",
                ticketId.toString(),
                details,
                truncate(ipAddress, 45)));
    }

    private String truncate(String value, int maximumLength) {
        return value == null || value.length() <= maximumLength
                ? value
                : value.substring(0, maximumLength);
    }
}
