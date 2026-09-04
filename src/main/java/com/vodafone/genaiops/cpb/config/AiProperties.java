package com.vodafone.genaiops.cpb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String baseUrl,
        String fetchPath,
        String confirmPath,
        String apiKey,
        int connectTimeoutMs,
        int readTimeoutSeconds,
        int maxAttempts,
        int maxIterations,
        String noteAuthor,
        boolean confirmCallEnabled) {
}
