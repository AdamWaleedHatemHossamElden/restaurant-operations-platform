package com.adam.restaurantoperations.audit;

import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class ReservationAuditService {

    private final AuditLogRepository repository;

    public ReservationAuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(String action, Long userId, Long reservationId, Long tableId, String ipAddress) {
        Map<String, Object> details = tableId == null ? Map.of() : Map.of("tableId", tableId);
        repository.save(new AuditLogEntity(
                userId,
                action,
                "RESERVATION",
                reservationId.toString(),
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
