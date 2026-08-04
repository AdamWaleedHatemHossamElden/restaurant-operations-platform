package com.adam.restaurantoperations.audit;

import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class AuthenticationAuditService {

    private final AuditLogRepository repository;

    public AuthenticationAuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(String action, Long userId, String ipAddress, Map<String, Object> details) {
        repository.save(new AuditLogEntity(
                userId,
                action,
                "AUTHENTICATION",
                userId == null ? null : userId.toString(),
                details,
                truncate(ipAddress, 45)));
    }

    private String truncate(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength);
    }
}
