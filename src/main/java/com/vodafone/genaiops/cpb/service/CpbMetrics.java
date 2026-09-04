package com.vodafone.genaiops.cpb.service;

import com.vodafone.genaiops.cpb.enums.DispatchStatus;
import com.vodafone.genaiops.cpb.repository.AiDispatchRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** TASARIM_PLANI §11'de tanımlı metrikler — Prometheus'a `micrometer-registry-prometheus` ile
 * otomatik export edilir (`/actuator/prometheus`). */
@Component
@RequiredArgsConstructor
public class CpbMetrics {

    private final MeterRegistry meterRegistry;
    private final AiDispatchRepository aiDispatchRepository;

    private Counter dispatchClaimedCounter;
    private Counter aiCallFailuresCounter;
    private Counter inboxWrittenCounter;
    private Timer aiCallDurationTimer;

    @jakarta.annotation.PostConstruct
    void init() {
        dispatchClaimedCounter = Counter.builder("cpb_dispatch_claimed_total")
                .description("Claim edilen ai_dispatch satiri sayisi").register(meterRegistry);
        aiCallFailuresCounter = Counter.builder("cpb_ai_call_failures_total")
                .description("AI Agent'a yapilan basarisiz cagri sayisi (son deneme dahil)").register(meterRegistry);
        inboxWrittenCounter = Counter.builder("cpb_inbox_written_total")
                .description("ai_action_inbox'a yazilan kayit sayisi").register(meterRegistry);
        aiCallDurationTimer = Timer.builder("cpb_ai_call_duration_seconds")
                .description("AI Agent cagrisinin (son deneme) suresi").register(meterRegistry);
        meterRegistry.gauge("cpb_pending_dispatch", aiDispatchRepository, this::countPending);
    }

    public void incrementDispatchClaimed(int count) {
        dispatchClaimedCounter.increment(count);
    }

    public void recordAiCallDuration(long millis) {
        aiCallDurationTimer.record(millis, TimeUnit.MILLISECONDS);
    }

    public void incrementAiCallFailure() {
        aiCallFailuresCounter.increment();
    }

    public void incrementInboxWritten() {
        inboxWrittenCounter.increment();
    }

    private double countPending(AiDispatchRepository repo) {
        return repo.countByStatus(DispatchStatus.PENDING);
    }
}
