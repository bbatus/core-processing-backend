package com.vodafone.genaiops.cpb.scheduler;

import com.vodafone.genaiops.cpb.service.DispatchClaimService;
import com.vodafone.genaiops.cpb.service.DispatchProcessingService;
import com.vodafone.genaiops.cpb.service.FlowGuard;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DispatchPollerScheduler {

    private final DispatchClaimService dispatchClaimService;
    private final DispatchProcessingService dispatchProcessingService;
    private final FlowGuard flowGuard;

    @Scheduled(fixedDelayString = "${app.dispatch.poll-interval-ms}")
    public void poll() {
        if (!flowGuard.isPollEnabled()) {
            return;
        }
        List<Long> claimed = dispatchClaimService.claimBatch();
        for (Long dispatchId : claimed) {
            try {
                dispatchProcessingService.process(dispatchId);
            } catch (Exception e) {
                log.error("Dispatch isleme sirasinda beklenmeyen hata: dispatchId={}", dispatchId, e);
            }
        }
    }

    @Scheduled(fixedDelayString = "#{${app.dispatch.claim-timeout-minutes} * 60000}")
    public void reapStaleClaims() {
        if (!flowGuard.isPollEnabled()) {
            return;
        }
        dispatchClaimService.reapStaleClaims();
    }
}
