package com.vodafone.genaiops.cpb.scheduler;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vodafone.genaiops.cpb.service.DispatchClaimService;
import com.vodafone.genaiops.cpb.service.DispatchProcessingService;
import com.vodafone.genaiops.cpb.service.FlowGuard;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DispatchPollerSchedulerTest {

    @Mock
    private DispatchClaimService dispatchClaimService;
    @Mock
    private DispatchProcessingService dispatchProcessingService;
    @Mock
    private FlowGuard flowGuard;

    private DispatchPollerScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new DispatchPollerScheduler(dispatchClaimService, dispatchProcessingService, flowGuard);
    }

    @Test
    void poll_pollKapaliysaHicBirSeyYapmaz() {
        when(flowGuard.isPollEnabled()).thenReturn(false);

        scheduler.poll();

        verify(dispatchClaimService, never()).claimBatch();
        verify(dispatchProcessingService, never()).process(anyLong());
    }

    @Test
    void poll_claimEdilenHerIsIcinProcessCagrilir() {
        when(flowGuard.isPollEnabled()).thenReturn(true);
        when(dispatchClaimService.claimBatch()).thenReturn(List.of(1L, 2L, 3L));

        scheduler.poll();

        verify(dispatchProcessingService).process(1L);
        verify(dispatchProcessingService).process(2L);
        verify(dispatchProcessingService).process(3L);
    }

    @Test
    void poll_claimEdilenBosSeIseProcessHicCagrilmaz() {
        when(flowGuard.isPollEnabled()).thenReturn(true);
        when(dispatchClaimService.claimBatch()).thenReturn(List.of());

        scheduler.poll();

        verify(dispatchProcessingService, never()).process(anyLong());
    }

    @Test
    void poll_birIsinHatasiDigerlerininIslenmesiniEngellemez() {
        when(flowGuard.isPollEnabled()).thenReturn(true);
        when(dispatchClaimService.claimBatch()).thenReturn(List.of(1L, 2L, 3L));
        doThrow(new RuntimeException("beklenmeyen hata")).when(dispatchProcessingService).process(2L);

        scheduler.poll();

        verify(dispatchProcessingService).process(1L);
        verify(dispatchProcessingService).process(2L);
        verify(dispatchProcessingService).process(3L);
    }

    @Test
    void reapStaleClaims_pollKapaliysaCagrilmaz() {
        when(flowGuard.isPollEnabled()).thenReturn(false);

        scheduler.reapStaleClaims();

        verify(dispatchClaimService, never()).reapStaleClaims();
    }

    @Test
    void reapStaleClaims_pollAcikkenCagrilir() {
        when(flowGuard.isPollEnabled()).thenReturn(true);

        scheduler.reapStaleClaims();

        verify(dispatchClaimService, times(1)).reapStaleClaims();
    }
}
