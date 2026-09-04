package com.vodafone.genaiops.cpb.service;

import com.vodafone.genaiops.cpb.client.AiAgentClient;
import com.vodafone.genaiops.cpb.config.AiProperties;
import com.vodafone.genaiops.cpb.dto.AiCallResult;
import com.vodafone.genaiops.cpb.dto.AiFetchRequest;
import com.vodafone.genaiops.cpb.entity.AiDispatch;
import com.vodafone.genaiops.cpb.entity.AiInteraction;
import com.vodafone.genaiops.cpb.entity.AiProcess;
import com.vodafone.genaiops.cpb.entity.Ticket;
import com.vodafone.genaiops.cpb.entity.TicketContext;
import com.vodafone.genaiops.cpb.enums.AiProcessStatus;
import com.vodafone.genaiops.cpb.enums.AuditCategory;
import com.vodafone.genaiops.cpb.enums.DispatchStatus;
import com.vodafone.genaiops.cpb.mapper.ContextRequestMapper;
import com.vodafone.genaiops.cpb.repository.AiDispatchRepository;
import com.vodafone.genaiops.cpb.repository.AiInteractionRepository;
import com.vodafone.genaiops.cpb.repository.AiProcessRepository;
import com.vodafone.genaiops.cpb.repository.TicketContextRepository;
import com.vodafone.genaiops.cpb.repository.TicketRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bir dispatch'in tam islenmesini yonetir: context'i AI istegine cevirir, AI Agent'i cagirir, her
 * denemeyi ({@code ai_interaction}) ve tur ozetini ({@code ai_process}) kaydeder, sonucu
 * {@code ai_action_inbox}'a yazar, {@code ai_dispatch} durumunu kapatir. Bkz.
 * CPB_TASARIM_VE_GELISTIRME_PLANI §3.2/§9.3.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchProcessingService {

    private final TicketRepository ticketRepository;
    private final TicketContextRepository ticketContextRepository;
    private final AiDispatchRepository aiDispatchRepository;
    private final AiProcessRepository aiProcessRepository;
    private final AiInteractionRepository aiInteractionRepository;
    private final ContextRequestMapper contextRequestMapper;
    private final AiAgentClient aiAgentClient;
    private final ActionInboxWriter actionInboxWriter;
    private final AuditLogService auditLogService;
    private final FlowGuard flowGuard;
    private final AiProperties aiProperties;
    private final CpbMetrics cpbMetrics;

    @Transactional
    public void process(Long dispatchId) {
        MDC.put("dispatchId", String.valueOf(dispatchId));
        MDC.put("correlationId", UUID.randomUUID().toString());
        try {
            doProcess(dispatchId);
        } finally {
            MDC.clear();
        }
    }

    private void doProcess(Long dispatchId) {
        AiDispatch dispatch = aiDispatchRepository.findById(dispatchId).orElse(null);
        if (dispatch == null || dispatch.getStatus() != DispatchStatus.CLAIMED) {
            // EP tarafinda arada CANCELLED/OBSOLETE olmus olabilir (ticket baska gruba gecti vb.) —
            // bkz. §9.3: bu durumda DCase'e bosuna yorum yazilmamasi icin sessizce atlanir.
            log.info("Dispatch artik CLAIMED degil, atlaniyor: dispatchId={}", dispatchId);
            return;
        }
        MDC.put("version", String.valueOf(dispatch.getVersion()));

        if (!flowGuard.isAiCallEnabled()) {
            revertToPending(dispatch);
            auditLogService.record(AuditCategory.CPB_SKIPPED_KILL_SWITCH, ticketDcaseId(dispatch),
                    Map.of("dispatchId", dispatch.getId(), "reason", "FLOW_AI_CALL_ENABLED=false"));
            return;
        }

        Ticket ticket = ticketRepository.findById(dispatch.getTicketId()).orElseThrow();
        TicketContext context = ticketContextRepository.findById(dispatch.getContextId()).orElseThrow();
        int iteration = dispatch.getVersion();
        MDC.put("dcaseTicketId", String.valueOf(ticket.getDcaseTicketId()));
        MDC.put("iteration", String.valueOf(iteration));
        auditLogService.record(AuditCategory.CPB_DISPATCH_CLAIMED, ticket.getDcaseTicketId(),
                Map.of("dispatchId", dispatch.getId(), "triggerRule", String.valueOf(dispatch.getTriggerRule())));
        boolean maxIterationsReached = aiProperties.maxIterations() > 0 && iteration > aiProperties.maxIterations();

        AiProcess process = startProcess(dispatch, ticket, iteration);
        if (process.getStatus() != AiProcessStatus.IN_PROGRESS) {
            // Bu dispatch/iteration icin daha once TAMAMLANMIS bir surec var (orn. dispatch elle ya
            // da bir hata sonrasi tekrar PENDING'e dusmus) — AI Agent'i GEREKSIZ YERE tekrar cagirmak
            // yerine (pahali + §6.6'nin ruhuna aykiri) yalnizca dispatch'i o sonuca gore senkron eder.
            log.info("Bu dispatch/iteration icin AiProcess zaten sonuclanmis, AI tekrar cagrilmiyor: "
                    + "dispatchId={}, processStatus={}", dispatch.getId(), process.getStatus());
            if (process.getStatus() == AiProcessStatus.SUCCEEDED) {
                completeDispatch(dispatch);
            } else {
                failDispatch(dispatch, process.getErrorMessage());
            }
            return;
        }

        if (maxIterationsReached) {
            actionInboxWriter.writeProposal(dispatch, ticket, null, true);
            cpbMetrics.incrementInboxWritten();
            finishProcess(process, AiProcessStatus.SUCCEEDED, null, "MAX_ITERATIONS_REACHED");
            completeDispatch(dispatch);
            auditLogService.record(AuditCategory.CPB_MAX_ITERATIONS_REACHED, ticket.getDcaseTicketId(),
                    Map.of("dispatchId", dispatch.getId(), "iteration", iteration));
            return;
        }

        AiFetchRequest request = contextRequestMapper.toRequest(ticket, context, dispatch, iteration);
        List<AiCallResult> attempts = aiAgentClient.fetchWithRetries(request);
        persistInteractions(process, attempts);

        AiCallResult last = attempts.get(attempts.size() - 1);
        cpbMetrics.recordAiCallDuration(last.durationMs());
        if (!last.success()) {
            cpbMetrics.incrementAiCallFailure();
            finishProcess(process, AiProcessStatus.FAILED, null, last.errorMessage());
            failDispatch(dispatch, last.errorMessage());
            auditLogService.record(AuditCategory.CPB_AI_CALL_FAILED, ticket.getDcaseTicketId(),
                    Map.of("dispatchId", dispatch.getId(), "error", String.valueOf(last.errorMessage())));
            return;
        }

        actionInboxWriter.writeProposal(dispatch, ticket, last.response(), false);
        cpbMetrics.incrementInboxWritten();
        finishProcess(process, AiProcessStatus.SUCCEEDED, last.response().solutionUniqueid(),
                last.response().solution());
        completeDispatch(dispatch);
        auditLogService.record(AuditCategory.CPB_AI_CALL_SUCCEEDED, ticket.getDcaseTicketId(),
                Map.of("dispatchId", dispatch.getId(), "solutionUniqueid",
                        String.valueOf(last.response().solutionUniqueid())));
        auditLogService.record(AuditCategory.CPB_INBOX_WRITTEN, ticket.getDcaseTicketId(),
                Map.of("dispatchId", dispatch.getId()));
    }

    private AiProcess startProcess(AiDispatch dispatch, Ticket ticket, int iteration) {
        return aiProcessRepository.findByDispatchIdAndIteration(dispatch.getId(), iteration).orElseGet(() -> {
            AiProcess process = new AiProcess();
            process.setDispatchId(dispatch.getId());
            process.setTicketId(dispatch.getTicketId());
            process.setDcaseTicketId(ticket.getDcaseTicketId());
            process.setVersion(dispatch.getVersion());
            process.setIteration(iteration);
            process.setTriggerRule(dispatch.getTriggerRule());
            process.setStatus(AiProcessStatus.IN_PROGRESS);
            process.setStartedAt(LocalDateTime.now());
            return aiProcessRepository.save(process);
        });
    }

    private void persistInteractions(AiProcess process, List<AiCallResult> attempts) {
        for (int i = 0; i < attempts.size(); i++) {
            AiCallResult attempt = attempts.get(i);
            AiInteraction interaction = new AiInteraction();
            interaction.setProcessId(process.getId());
            interaction.setEndpoint("fetch");
            interaction.setHttpMethod("POST");
            interaction.setAttemptNo(i + 1);
            interaction.setRequestBody(attempt.requestBodyJson());
            interaction.setResponseStatus(attempt.httpStatus());
            interaction.setResponseBody(attempt.responseBodyRaw());
            interaction.setErrorMessage(attempt.errorMessage());
            interaction.setDurationMs(attempt.durationMs());
            interaction.setCreatedAt(LocalDateTime.now());
            aiInteractionRepository.save(interaction);
        }
    }

    private void finishProcess(AiProcess process, AiProcessStatus status, String solutionUniqueid,
            String solutionOrError) {
        process.setStatus(status);
        process.setFinishedAt(LocalDateTime.now());
        process.setDurationMs(java.time.Duration.between(process.getStartedAt(), process.getFinishedAt()).toMillis());
        if (status == AiProcessStatus.FAILED) {
            process.setErrorMessage(solutionOrError);
        } else {
            process.setSolutionUniqueid(solutionUniqueid);
            process.setSolutionText(solutionOrError);
        }
        aiProcessRepository.save(process);
    }

    private void completeDispatch(AiDispatch dispatch) {
        dispatch.setStatus(DispatchStatus.COMPLETED);
        dispatch.setCompletedAt(LocalDateTime.now());
        aiDispatchRepository.save(dispatch);
    }

    private void failDispatch(AiDispatch dispatch, String errorMessage) {
        dispatch.setStatus(DispatchStatus.FAILED);
        dispatch.setErrorMessage(errorMessage);
        aiDispatchRepository.save(dispatch);
    }

    private void revertToPending(AiDispatch dispatch) {
        dispatch.setStatus(DispatchStatus.PENDING);
        dispatch.setClaimedBy(null);
        dispatch.setClaimedAt(null);
        aiDispatchRepository.save(dispatch);
    }

    private java.util.UUID ticketDcaseId(AiDispatch dispatch) {
        return ticketRepository.findById(dispatch.getTicketId()).map(Ticket::getDcaseTicketId).orElse(null);
    }
}
