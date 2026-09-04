package com.vodafone.genaiops.cpb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** K3 deseni (EP ile ayni mantik): master + alt bayraklar, efektif deger her zaman
 * {@code enabled() && alt.enabled()}. Bayraklar pod acilisinda okunur — degisiklik sonrasi
 * {@code oc rollout restart} gerekir. */
@ConfigurationProperties(prefix = "flow")
public record FlowProperties(boolean enabled, CpbPoll cpbPoll, AiCall aiCall, InboxWrite inboxWrite) {

    public record CpbPoll(boolean enabled) {
    }

    public record AiCall(boolean enabled) {
    }

    public record InboxWrite(boolean enabled) {
    }
}
