package com.vodafone.genaiops.cpb.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vodafone.genaiops.cpb.entity.AuditLog;
import com.vodafone.genaiops.cpb.enums.AuditCategory;
import com.vodafone.genaiops.cpb.repository.AuditLogRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLogService service;

    @BeforeEach
    void setUp() {
        service = new AuditLogService(auditLogRepository, new ObjectMapper());
    }

    @Test
    void record_kategoriTicketIdVeDetayDoguSekildeKaydedilir() {
        UUID dcaseTicketId = UUID.randomUUID();

        service.write(AuditCategory.CPB_AI_CALL_SUCCEEDED, dcaseTicketId, Map.of("dispatchId", 42L));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getCategory()).isEqualTo(AuditCategory.CPB_AI_CALL_SUCCEEDED);
        assertThat(saved.getDcaseTicketId()).isEqualTo(dcaseTicketId);
        assertThat(saved.getDetail()).contains("dispatchId").contains("42");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void record_dcaseTicketIdNullOlabilir() {
        service.write(AuditCategory.CPB_SKIPPED_KILL_SWITCH, null, Map.of("reason", "FLOW_AI_CALL_ENABLED=false"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getDcaseTicketId()).isNull();
    }

    @Test
    void record_jsonSerilestirmeBasarisizOlursaBosObjeYazarPatlamaz() throws JsonProcessingException {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(new RuntimeException("serialize edilemedi"));
        AuditLogService serviceWithFailingMapper = new AuditLogService(auditLogRepository, failingMapper);

        serviceWithFailingMapper.write(AuditCategory.CPB_AI_CALL_FAILED, UUID.randomUUID(), Map.of("x", new Object()));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getDetail()).isEqualTo("{}");
    }
}
