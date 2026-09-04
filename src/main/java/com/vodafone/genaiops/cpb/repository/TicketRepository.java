package com.vodafone.genaiops.cpb.repository;

import com.vodafone.genaiops.cpb.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
}
