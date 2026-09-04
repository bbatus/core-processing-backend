package com.vodafone.genaiops.cpb.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vodafone.genaiops.cpb.config.DispatchProperties;
import com.vodafone.genaiops.cpb.entity.AiDispatch;
import com.vodafone.genaiops.cpb.enums.DispatchStatus;
import com.vodafone.genaiops.cpb.repository.AiDispatchRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DispatchClaimServiceTest {

    @Mock
    private AiDispatchRepository aiDispatchRepository;
    @Mock
    private CpbMetrics cpbMetrics;

    private DispatchClaimService service;

    @BeforeEach
    void setUp() {
        DispatchProperties props = new DispatchProperties(2000, 5, 15);
        service = new DispatchClaimService(aiDispatchRepository, props, cpbMetrics);
    }

    @Test
    void claimBatch_bulunanIslerCLAIMEDIsaretlenirVeMetrikArtar() {
        AiDispatch d1 = pending(1L);
        AiDispatch d2 = pending(2L);
        when(aiDispatchRepository.findPendingForUpdateSkipLocked(5)).thenReturn(List.of(d1, d2));
        when(aiDispatchRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<Long> claimedIds = service.claimBatch();

        assertThat(claimedIds).containsExactly(1L, 2L);
        assertThat(d1.getStatus()).isEqualTo(DispatchStatus.CLAIMED);
        assertThat(d1.getClaimedAt()).isNotNull();
        assertThat(d1.getClaimedBy()).isNotBlank();
        assertThat(d2.getStatus()).isEqualTo(DispatchStatus.CLAIMED);
        verify(cpbMetrics).incrementDispatchClaimed(2);
    }

    @Test
    void claimBatch_bosSonucMetrikArtirmaz() {
        when(aiDispatchRepository.findPendingForUpdateSkipLocked(anyInt())).thenReturn(List.of());
        when(aiDispatchRepository.saveAll(any())).thenReturn(List.of());

        List<Long> claimedIds = service.claimBatch();

        assertThat(claimedIds).isEmpty();
        verify(cpbMetrics, never()).incrementDispatchClaimed(org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void reapStaleClaims_uzunSureClaimedeKalanlarPendingeDoner() {
        AiDispatch stale = new AiDispatch();
        stale.setId(5L);
        stale.setStatus(DispatchStatus.CLAIMED);
        stale.setClaimedBy("dead-pod");
        stale.setClaimedAt(LocalDateTime.now().minusMinutes(30));
        when(aiDispatchRepository.findStaleClaimed(any())).thenReturn(List.of(stale));
        when(aiDispatchRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        service.reapStaleClaims();

        assertThat(stale.getStatus()).isEqualTo(DispatchStatus.PENDING);
        assertThat(stale.getClaimedBy()).isNull();
        assertThat(stale.getClaimedAt()).isNull();
        verify(aiDispatchRepository, times(1)).saveAll(any());
    }

    private AiDispatch pending(Long id) {
        AiDispatch d = new AiDispatch();
        d.setId(id);
        d.setStatus(DispatchStatus.PENDING);
        return d;
    }
}
