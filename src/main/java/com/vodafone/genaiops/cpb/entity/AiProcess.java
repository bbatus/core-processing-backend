package com.vodafone.genaiops.cpb.entity;

import com.vodafone.genaiops.cpb.enums.AiProcessStatus;
import com.vodafone.genaiops.cpb.enums.TriggerRule;
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

/** CPB'ye ozgu tablo — bir dispatch'in bir turunun (iteration) ozet kaydi. "AI ne yapmak istedi,
 * ne dondu" sorusunun ozet cevabi burada; ham istek/yanit icin bkz. {@link AiInteraction}. */
@Entity
@Table(name = "ai_process")
@Getter
@Setter
public class AiProcess {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ai_process_id_seq")
    @SequenceGenerator(name = "ai_process_id_seq", sequenceName = "ai_process_id_seq", allocationSize = 50)
    private Long id;

    @Column(name = "dispatch_id")
    private Long dispatchId;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "dcase_ticket_id")
    private UUID dcaseTicketId;

    private Integer version;
    private Integer iteration;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_rule")
    private TriggerRule triggerRule;

    @Enumerated(EnumType.STRING)
    private AiProcessStatus status;

    @Column(name = "solution_uniqueid")
    private String solutionUniqueid;

    @Column(name = "solution_text")
    private String solutionText;

    @Column(name = "ai_status")
    private String aiStatus;

    @Column(name = "inbox_action_id")
    private Long inboxActionId;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "duration_ms")
    private Long durationMs;
}
