package com.vodafone.genaiops.cpb.entity;

import com.vodafone.genaiops.cpb.enums.AuditCategory;
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

/** EP'nin PAYLAŞILAN {@code audit_log} tablosu — CPB yalnizca INSERT eder (EP'nin kendi kayitlarina
 * asla dokunmaz). Retention/purge yok, kalicidir (EP'nin bilincli karari, bkz. EP CLAUDE.md §7). */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "audit_log_id_seq")
    @SequenceGenerator(name = "audit_log_id_seq", sequenceName = "audit_log_id_seq", allocationSize = 50)
    private Long id;

    @Enumerated(EnumType.STRING)
    private AuditCategory category;

    @Column(name = "dcase_ticket_id")
    private UUID dcaseTicketId;

    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String detail;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
