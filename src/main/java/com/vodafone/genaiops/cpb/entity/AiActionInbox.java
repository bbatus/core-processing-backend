package com.vodafone.genaiops.cpb.entity;

import com.vodafone.genaiops.cpb.enums.ActionInboxStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * EP'nin {@code ai_action_inbox} tablosu — PAYLAŞILAN (K2). CPB burada TEK yazma yetkisine sahiptir:
 * INSERT (yeni oneri/kapanis aksiyonu). EP'nin poller'i ({@code ActionInboxPoller}) bunu okuyup
 * DCase'e uygular ve status'u gunceller — CPB status'a bir daha DOKUNMAZ.
 *
 * <p>⚠️ ADR-08 CPB baglayici kurali: sequence adi ({@code ai_action_inbox_id_seq}) ve
 * {@code allocationSize} (50) EP'nin migration'iyla BIREBIR AYNI olmak zorunda — farkli olursa ID
 * cakisir. Sequence EP'nin migration'inda olusturulur, CPB TEKRAR OLUSTURMAZ.</p>
 */
@Entity
@Table(name = "ai_action_inbox")
@Getter
@Setter
public class AiActionInbox {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ai_action_inbox_id_seq")
    @SequenceGenerator(name = "ai_action_inbox_id_seq", sequenceName = "ai_action_inbox_id_seq", allocationSize = 50)
    private Long id;

    @Column(name = "source_message_id")
    private UUID sourceMessageId;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "dispatch_id")
    private Long dispatchId;

    private Integer version;

    @Column(name = "action_type")
    private String actionType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dcase_update_payload", columnDefinition = "jsonb")
    private String dcaseUpdatePayload;

    @Column(name = "is_compensation")
    private boolean compensation;

    @Column(name = "requires_approval")
    private boolean requiresApproval;

    @Enumerated(EnumType.STRING)
    private ActionInboxStatus status;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "discarded_at")
    private LocalDateTime discardedAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
