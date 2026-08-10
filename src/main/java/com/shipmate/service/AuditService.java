package com.shipmate.service;

import com.shipmate.model.App;
import com.shipmate.model.Alert;
import com.shipmate.repository.AlertRepository;
import com.shipmate.repository.AppRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final com.shipmate.repository.AuditLogRepository auditRepo;

    public AuditService(com.shipmate.repository.AuditLogRepository auditRepo) {
        this.auditRepo = auditRepo;
    }

    public void log(String actor, String action, String detail) {
        com.shipmate.model.AuditLog entry = new com.shipmate.model.AuditLog();
        entry.setActor(actor);
        entry.setAction(action);
        entry.setDetail(detail);
        auditRepo.save(entry);
        log.info("[{}] {} - {}", actor, action, detail);
    }
}
