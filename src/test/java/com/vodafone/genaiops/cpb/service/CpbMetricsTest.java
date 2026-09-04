package com.vodafone.genaiops.cpb.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.vodafone.genaiops.cpb.enums.DispatchStatus;
import com.vodafone.genaiops.cpb.repository.AiDispatchRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CpbMetricsTest {

    @Mock
    private AiDispatchRepository aiDispatchRepository;

    private MeterRegistry registry;
    private CpbMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new CpbMetrics(registry, aiDispatchRepository);
        metrics.init();
    }

    @Test
    void incrementDispatchClaimed_sayaciBelirtilenKadarArtirir() {
        metrics.incrementDispatchClaimed(3);
        metrics.incrementDispatchClaimed(2);

        assertThat(registry.get("cpb_dispatch_claimed_total").counter().count()).isEqualTo(5.0);
    }

    @Test
    void incrementAiCallFailure_sayaciBirArtirir() {
        metrics.incrementAiCallFailure();
        metrics.incrementAiCallFailure();

        assertThat(registry.get("cpb_ai_call_failures_total").counter().count()).isEqualTo(2.0);
    }

    @Test
    void incrementInboxWritten_sayaciBirArtirir() {
        metrics.incrementInboxWritten();

        assertThat(registry.get("cpb_inbox_written_total").counter().count()).isEqualTo(1.0);
    }

    @Test
    void recordAiCallDuration_timerKaydeder() {
        metrics.recordAiCallDuration(500);
        metrics.recordAiCallDuration(1500);

        var timer = registry.get("cpb_ai_call_duration_seconds").timer();
        assertThat(timer.count()).isEqualTo(2);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isCloseTo(2000.0, org.assertj.core.data.Offset.offset(50.0));
    }

    @Test
    void pendingDispatchGauge_repositorydenOkurLockOlusturanSorguyuKullanmaz() {
        when(aiDispatchRepository.countByStatus(DispatchStatus.PENDING)).thenReturn(7L);

        double value = registry.get("cpb_pending_dispatch").gauge().value();

        assertThat(value).isEqualTo(7.0);
        org.mockito.Mockito.verify(aiDispatchRepository, org.mockito.Mockito.never())
                .findPendingForUpdateSkipLocked(org.mockito.ArgumentMatchers.anyInt());
    }
}
