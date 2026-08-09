package com.adam.restaurantoperations.audit;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class OrderAuditService {
    private final AuditLogRepository repository;

    public OrderAuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(
            String action,
            Long userId,
            Long orderId,
            String orderNumber,
            Long relatedEntityId,
            String ipAddress) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("orderNumber", orderNumber);
        if (relatedEntityId != null) {
            details.put("relatedEntityId", relatedEntityId);
        }
        repository.save(new AuditLogEntity(
                userId,
                action,
                "ORDER",
                orderId.toString(),
                details,
                truncate(ipAddress, 45)));
    }

    private String truncate(String value, int maximumLength) {
        return value == null || value.length() <= maximumLength
                ? value
                : value.substring(0, maximumLength);
    }
}
