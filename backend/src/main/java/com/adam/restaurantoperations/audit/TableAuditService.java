package com.adam.restaurantoperations.audit;

import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class TableAuditService {

    private final AuditLogRepository repository;

    public TableAuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(String action, Long userId, Long tableId, String ipAddress) {
        repository.save(new AuditLogEntity(
                userId,
                action,
                "RESTAURANT_TABLE",
                tableId.toString(),
                Map.of(),
                truncate(ipAddress, 45)));
    }

    private String truncate(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength);
    }
}
