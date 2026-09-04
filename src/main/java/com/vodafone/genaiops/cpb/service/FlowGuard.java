package com.vodafone.genaiops.cpb.service;

import com.vodafone.genaiops.cpb.config.FlowProperties;
import com.vodafone.genaiops.cpb.exception.FlowDisabledException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Efektif deger her zaman {@code flow.enabled && flow.X.enabled} — EP'nin FlowGuard'iyla ayni
 * desen (bkz. genaiops-event-processor CLAUDE.md §5). */
@Component
@RequiredArgsConstructor
public class FlowGuard {

    private final FlowProperties flowProperties;

    public boolean isPollEnabled() {
        return flowProperties.enabled() && flowProperties.cpbPoll().enabled();
    }

    public boolean isAiCallEnabled() {
        return flowProperties.enabled() && flowProperties.aiCall().enabled();
    }

    public boolean isInboxWriteEnabled() {
        return flowProperties.enabled() && flowProperties.inboxWrite().enabled();
    }

    public void assertAiCallEnabled() {
        if (!isAiCallEnabled()) {
            throw new FlowDisabledException("FLOW_AI_CALL_ENABLED=false (veya FLOW_ENABLED=false)");
        }
    }

    public void assertInboxWriteEnabled() {
        if (!isInboxWriteEnabled()) {
            throw new FlowDisabledException("FLOW_INBOX_WRITE_ENABLED=false (veya FLOW_ENABLED=false)");
        }
    }
}
