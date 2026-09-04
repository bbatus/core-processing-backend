package com.vodafone.genaiops.cpb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.dispatch")
public record DispatchProperties(int pollIntervalMs, int batchSize, int claimTimeoutMinutes) {
}
