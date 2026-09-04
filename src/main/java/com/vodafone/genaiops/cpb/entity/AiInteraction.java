package com.vodafone.genaiops.cpb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** CPB'ye ozgu tablo — AI Agent'a yapilan HER HTTP cagrisinin ham izi (retry'lar dahil, tam
 * istek/yanit govdesi, kirpilmadan). Token/parola gibi kimlik bilgileri BURAYA YAZILMAZ (header'da
 * tasinir, loglanmaz) — EP'nin API_AUDIT maskeleme prensibiyle ayni. */
@Entity
@Table(name = "ai_interaction")
@Getter
@Setter
public class AiInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ai_interaction_id_seq")
    @SequenceGenerator(name = "ai_interaction_id_seq", sequenceName = "ai_interaction_id_seq", allocationSize = 50)
    private Long id;

    @Column(name = "process_id")
    private Long processId;

    private String endpoint;

    @Column(name = "http_method")
    private String httpMethod;

    @Column(name = "attempt_no")
    private Integer attemptNo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_body", columnDefinition = "jsonb")
    private String requestBody;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
