package com.adam.restaurantoperations.audit;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class PaymentAuditService {
    private final AuditLogRepository repository;

    public PaymentAuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(
            String action,
            Long actorId,
            String entityType,
            Long entityId,
            Map<String, Object> safeDetails,
            String ipAddress) {
        repository.save(new AuditLogEntity(
                actorId,
                action,
                entityType,
                entityId.toString(),
                new LinkedHashMap<>(safeDetails),
                truncate(ipAddress, 45)));
    }

    private String truncate(String value, int maximumLength) {
        return value == null || value.length() <= maximumLength
                ? value
                : value.substring(0, maximumLength);
    }
}
