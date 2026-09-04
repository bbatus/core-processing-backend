package com.vodafone.genaiops.cpb.service;

import com.vodafone.genaiops.cpb.config.DispatchProperties;
import com.vodafone.genaiops.cpb.entity.AiDispatch;
import com.vodafone.genaiops.cpb.enums.DispatchStatus;
import com.vodafone.genaiops.cpb.repository.AiDispatchRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code FOR UPDATE SKIP LOCKED} ile PENDING isleri claim eder — birden fazla CPB replikasi ayni
 * satiri almaz, ek bir dagitik kilide (Redis) gerek YOK. Bkz. CPB_TASARIM_VE_GELISTIRME_PLANI §5.1.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchClaimService {

    private final AiDispatchRepository aiDispatchRepository;
    private final DispatchProperties dispatchProperties;

    private static final String POD_NAME = System.getenv().getOrDefault("HOSTNAME", "cpb-local");

    @Transactional
    public List<Long> claimBatch() {
        List<AiDispatch> pending = aiDispatchRepository.findPendingForUpdateSkipLocked(dispatchProperties.batchSize());
        LocalDateTime now = LocalDateTime.now();
        for (AiDispatch dispatch : pending) {
            dispatch.setStatus(DispatchStatus.CLAIMED);
            dispatch.setClaimedBy(POD_NAME);
            dispatch.setClaimedAt(now);
        }
        aiDispatchRepository.saveAll(pending);
        if (!pending.isEmpty()) {
            log.info("Dispatch claim edildi: count={}, ids={}", pending.size(), pending.stream().map(AiDispatch::getId).toList());
        }
        return pending.stream().map(AiDispatch::getId).toList();
    }

    /** Worker cokup claim'i tamamlayamamissa (uzun sure CLAIMED'de kalmis) isi tekrar PENDING'e
     * dondurur — bkz. §9.3. */
    @Transactional
    public void reapStaleClaims() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(dispatchProperties.claimTimeoutMinutes());
        List<AiDispatch> stale = aiDispatchRepository.findStaleClaimed(threshold);
        for (AiDispatch dispatch : stale) {
            log.warn("Claim timeout - PENDING'e geri donduruluyor: dispatchId={}, claimedBy={}, claimedAt={}",
                    dispatch.getId(), dispatch.getClaimedBy(), dispatch.getClaimedAt());
            dispatch.setStatus(DispatchStatus.PENDING);
            dispatch.setClaimedBy(null);
            dispatch.setClaimedAt(null);
        }
        aiDispatchRepository.saveAll(stale);
    }
}
