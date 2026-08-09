package com.adam.restaurantoperations.audit;

import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MenuAuditService {
    private final AuditLogRepository repository;
    public MenuAuditService(AuditLogRepository repository){this.repository=repository;}
    public void record(String action,Long userId,String type,Long id,String ipAddress){repository.save(new AuditLogEntity(userId,action,type,id.toString(),Map.of(),truncate(ipAddress,45)));}
    private String truncate(String value,int max){return value==null||value.length()<=max?value:value.substring(0,max);}
}
