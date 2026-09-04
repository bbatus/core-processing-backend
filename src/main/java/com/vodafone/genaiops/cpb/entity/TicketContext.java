package com.vodafone.genaiops.cpb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** EP'nin {@code ticket_context} tablosunun SALT-OKUNUR aynasi — AI'a gidecek ham baglam burada. */
@Entity
@Table(name = "ticket_context")
@Getter
@Setter
public class TicketContext {

    @Id
    private Long id;

    @Column(name = "ticket_id")
    private Long ticketId;

    private Integer version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_json", columnDefinition = "jsonb")
    private String contextJson;

    @Column(name = "include_attachments")
    private boolean includeAttachments;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
