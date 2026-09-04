package com.vodafone.genaiops.cpb.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * EP'nin (genaiops-event-processor) {@code ticket} tablosunun SALT-OKUNUR aynasi. CPB bu tabloya
 * hicbir zaman yazmaz — {@code spring.jpa.hibernate.ddl-auto=validate} + Flyway bu tabloyu hic
 * yonetmez (EP'nin migration'lari zaten olusturmustur).
 */
@Entity
@Table(name = "ticket")
@Getter
@Setter
public class Ticket {

    @Id
    private Long id;

    @Column(name = "dcase_ticket_id")
    private UUID dcaseTicketId;

    @Column(name = "ticket_number")
    private Long ticketNumber;

    private String title;
    private String description;

    @Column(name = "current_version")
    private Integer currentVersion;

    private String status;

    @Column(name = "assigned_group")
    private String assignedGroup;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "assignee_name")
    private String assigneeName;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "customer_name")
    private String customerName;

    private String category;
    private String priority;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
