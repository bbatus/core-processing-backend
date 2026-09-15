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

    /**
     * <p><b>2026-09-15 — sozlesme hizalamasi:</b> "ek aksiyon gerekmiyor" karari artik AI'in serbest
     * {@code status} metninden ({@code "NO_ACTION_NEEDED"}) degil, rehberin §7'sindeki acik
     * {@code requiresApproval} alanindan okunur ({@link AiFetchResponse#needsApproval()} — alan bos
     * gelirse GUVENLI TARAF olan {@code true}'ya duser).</p>
     *
     * <p><b>R4 neden HER ZAMAN onay gerektirir:</b> Didar'in rehberi §12 senaryo 2'de "R4 direkt
     * SUCCESS ({@code requiresApproval=false})" diye bir kabul senaryosu var. Bunu BILEREK
     * uygulamiyoruz: Faz 2'nin temel guvenlik kurali "gercek aksiyonu her zaman bir insan uygular"
     * (TASARIM_PLANI §2) — ilk turda AI'in ticket'i insana hic ugramadan kapatabilmesi bu kurali
     * zayiflatir. Bu, urun sahibi/SD onayi gerektiren bir karar; Didar'a acik madde olarak
     * bildirilecek. R5/R6'da kapanis zaten bir insan dongusunden GECTIKTEN sonra olur, orada AI'in
     * karari onurlandirilir.</p>
     */
    private Decision decide(TriggerRule triggerRule, AiFetchResponse aiResponse, boolean maxIterationsReached) {
        if (maxIterationsReached) {
            return new Decision("MAX_ITERATIONS_REACHED", false,
                    "Maksimum AI tur sayısına ulaşıldı, case manuel olarak ele alınmalıdır.");
        }
        String solutionText = aiResponse != null ? aiResponse.solution() : "";

        if (triggerRule == TriggerRule.R4) {
            return new Decision("PROPOSE_RESOLUTION", true, solutionText);
        }
        if (aiResponse != null && !aiResponse.needsApproval()) {
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
