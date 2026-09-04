package com.vodafone.genaiops.cpb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vodafone.genaiops.cpb.entity.AuditLog;
import com.vodafone.genaiops.cpb.enums.AuditCategory;
import com.vodafone.genaiops.cpb.repository.AuditLogRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    /** İsim bilerek {@code record} DEĞİL — Java 16+'ta "record" kısıtlı bir tanımlayıcı (Sonar S6213). */
    public void write(AuditCategory category, UUID dcaseTicketId, Map<String, Object> detail) {
        AuditLog log = new AuditLog();
        log.setCategory(category);
        log.setDcaseTicketId(dcaseTicketId);
        log.setDetail(writeJson(detail));
        log.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        auditLogRepository.save(log);
    }

    private String writeJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            return "{}";
        }
    }
}
