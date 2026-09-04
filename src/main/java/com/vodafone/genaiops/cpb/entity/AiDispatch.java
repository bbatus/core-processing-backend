package com.vodafone.genaiops.cpb.entity;

import com.vodafone.genaiops.cpb.enums.DispatchStatus;
import com.vodafone.genaiops.cpb.enums.TriggerRule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * EP'nin {@code ai_dispatch} tablosu — PAYLAŞILAN (K2). CPB burada yalnizca {@code status},
 * {@code claimed_by}, {@code claimed_at}, {@code completed_at}, {@code error_message} alanlarini
 * gunceller (UPDATE) — hicbir zaman INSERT yapmaz (satirlari EP olusturur). Bu yuzden
 * {@code @GeneratedValue} YOK — id her zaman EP'den okunan mevcut bir degerdir.
 */
@Entity
@Table(name = "ai_dispatch")
@Getter
@Setter
public class AiDispatch {

    @Id
    private Long id;

    @Column(name = "ticket_id")
    private Long ticketId;

    private Integer version;

    @Column(name = "context_id")
    private Long contextId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_rule")
    private TriggerRule triggerRule;

    @Column(name = "trigger_event_id")
    private UUID triggerEventId;

    @Enumerated(EnumType.STRING)
    private DispatchStatus status;

    @Column(name = "claimed_by")
    private String claimedBy;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
