package com.vodafone.genaiops.cpb.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vodafone.genaiops.cpb.config.FlowProperties;
import com.vodafone.genaiops.cpb.exception.FlowDisabledException;
import org.junit.jupiter.api.Test;

class FlowGuardTest {

    @Test
    void masterKapaliysaHerSeyKapali() {
        FlowProperties props = new FlowProperties(false,
                new FlowProperties.CpbPoll(true), new FlowProperties.AiCall(true), new FlowProperties.InboxWrite(true));
        FlowGuard guard = new FlowGuard(props);

        assertThat(guard.isPollEnabled()).isFalse();
        assertThat(guard.isAiCallEnabled()).isFalse();
        assertThat(guard.isInboxWriteEnabled()).isFalse();
    }

    @Test
    void masterAcikAmaAltBayrakKapaliysaOSpesifikOzellikKapali() {
        FlowProperties props = new FlowProperties(true,
                new FlowProperties.CpbPoll(true), new FlowProperties.AiCall(false), new FlowProperties.InboxWrite(true));
        FlowGuard guard = new FlowGuard(props);

        assertThat(guard.isPollEnabled()).isTrue();
        assertThat(guard.isAiCallEnabled()).isFalse();
        assertThat(guard.isInboxWriteEnabled()).isTrue();
    }

    @Test
    void hepsiAcikkenAssertMetodlariIstisnaFirlatmaz() {
        FlowProperties props = new FlowProperties(true,
                new FlowProperties.CpbPoll(true), new FlowProperties.AiCall(true), new FlowProperties.InboxWrite(true));
        FlowGuard guard = new FlowGuard(props);

        guard.assertAiCallEnabled();
        guard.assertInboxWriteEnabled();
    }

    @Test
    void aiCallKapaliyaAssertIstisnaFirlatir() {
        FlowProperties props = new FlowProperties(true,
                new FlowProperties.CpbPoll(true), new FlowProperties.AiCall(false), new FlowProperties.InboxWrite(true));
        FlowGuard guard = new FlowGuard(props);

        assertThatThrownBy(guard::assertAiCallEnabled).isInstanceOf(FlowDisabledException.class);
    }

    @Test
    void inboxWriteKapaliyaAssertIstisnaFirlatir() {
        FlowProperties props = new FlowProperties(true,
                new FlowProperties.CpbPoll(true), new FlowProperties.AiCall(true), new FlowProperties.InboxWrite(false));
        FlowGuard guard = new FlowGuard(props);

        assertThatThrownBy(guard::assertInboxWriteEnabled).isInstanceOf(FlowDisabledException.class);
    }
}
