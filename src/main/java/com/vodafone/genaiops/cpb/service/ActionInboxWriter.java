package com.vodafone.genaiops.cpb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vodafone.genaiops.cpb.config.AiProperties;
import com.vodafone.genaiops.cpb.dto.AiFetchResponse;
import com.vodafone.genaiops.cpb.entity.AiActionInbox;
import com.vodafone.genaiops.cpb.entity.AiDispatch;
import com.vodafone.genaiops.cpb.entity.Ticket;
import com.vodafone.genaiops.cpb.enums.ActionInboxStatus;
import com.vodafone.genaiops.cpb.enums.TriggerRule;
import com.vodafone.genaiops.cpb.repository.AiActionInboxRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code ai_action_inbox} kaydını üretir — bkz. TASARIM_PLANI §7.3/§7.4.
 *
 * <p>DCase PATCH gövdesine ticket'ı hangi insana geri atayacağımız ({@code relatedParty
 * [role=assignee]}), EP'nin {@code ticket.previous_human_assignee_id/_name} alanlarından (2026-09-04,
 * V5 migration) okunur — onay gerektiren (requiresApproval=true) her aksiyonda, bu alan doluysa
 * eklenir. Bkz. TASARIM_PLANI §14-S9 (artık ÇÖZÜLDÜ).</p>
 */
@Component
@RequiredArgsConstructor
public class ActionInboxWriter {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final AiActionInboxRepository aiActionInboxRepository;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public AiActionInbox writeProposal(AiDispatch dispatch, Ticket ticket, AiFetchResponse aiResponse,
            boolean maxIterationsReached) {
        Decision decision = decide(dispatch.getTriggerRule(), aiResponse, maxIterationsReached);
        UUID sourceMessageId = deterministicId(dispatch.getId());

        return aiActionInboxRepository.findBySourceMessageId(sourceMessageId).orElseGet(() -> {
            AiActionInbox inbox = new AiActionInbox();
            inbox.setSourceMessageId(sourceMessageId);
            inbox.setTicketId(dispatch.getTicketId());
            inbox.setDispatchId(dispatch.getId());
            inbox.setVersion(dispatch.getVersion());
            inbox.setActionType(decision.actionType());
            inbox.setDcaseUpdatePayload(buildPayload(decision.solutionText(), decision.requiresApproval(), ticket));
            inbox.setCompensation(false);
            inbox.setRequiresApproval(decision.requiresApproval());
            inbox.setStatus(ActionInboxStatus.PENDING);
            inbox.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
            return aiActionInboxRepository.save(inbox);
        });
    }

    private Decision decide(TriggerRule triggerRule, AiFetchResponse aiResponse, boolean maxIterationsReached) {
        if (maxIterationsReached) {
            return new Decision("MAX_ITERATIONS_REACHED", false,
                    "Maksimum AI tur sayısına ulaşıldı, case manuel olarak ele alınmalıdır.");
        }
        String solutionText = aiResponse != null ? aiResponse.solution() : "";
        boolean noActionNeeded = aiResponse != null && "NO_ACTION_NEEDED".equalsIgnoreCase(aiResponse.status());

        if (triggerRule == TriggerRule.R4) {
            return new Decision("PROPOSE_RESOLUTION", true, solutionText);
        }
        // R5/R6: yalnızca AI acikca "ek aksiyon gerekmiyor" derse kapanis; aksi halde oneri
        // devam ediyor sayilir (bkz. TASARIM_PLANI §7.4 karar mantigi).
        if (noActionNeeded) {
            return new Decision("NO_ACTION_NEEDED", false, solutionText);
        }
        return new Decision("PROPOSE_RESOLUTION", true, solutionText);
    }

    private String buildPayload(String solutionText, boolean requiresApproval, Ticket ticket) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode notes = root.putArray("note");
        ObjectNode note = notes.addObject();
        note.put("author", aiProperties.noteAuthor());
        note.put("date", OffsetDateTime.now(ZoneOffset.UTC).format(ISO));
        note.put("text", solutionText == null ? "" : solutionText);

        // Onay gerekiyorsa ve daha once bir insan bu ticket'i tutuyorduysa (R4 tetiklenmeden once),
        // ticket'i o kisiye geri atariz - EP'nin gercek PATCH ornegiyle (masterbysolutiondesigner
        // §6.5) birebir ayni TMF621 sekli.
        if (requiresApproval && ticket != null && ticket.getPreviousHumanAssigneeId() != null) {
            ArrayNode relatedParty = root.putArray("relatedParty");
            ObjectNode assignee = relatedParty.addObject();
            assignee.put("role", "assignee");
            ObjectNode party = assignee.putObject("partyOrPartyRole");
            party.put("@type", "PartyRef");
            party.put("id", ticket.getPreviousHumanAssigneeId().toString());
            if (ticket.getPreviousHumanAssigneeName() != null) {
                party.put("name", ticket.getPreviousHumanAssigneeName());
            }
        }
        return root.toString();
    }

    /** Aynı dispatch için (retry/reprocess durumunda) idempotent bir kimlik üretir — bkz. §6.6. */
    private UUID deterministicId(Long dispatchId) {
        return UUID.nameUUIDFromBytes(("dispatch:" + dispatchId).getBytes(StandardCharsets.UTF_8));
    }

    private record Decision(String actionType, boolean requiresApproval, String solutionText) {
    }
}
