package com.vodafone.genaiops.cpb.config;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
public class AiAgentClientConfig {

    private final AiProperties aiProperties;

    @Bean
    public RestClient aiAgentRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(aiProperties.connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofSeconds(aiProperties.readTimeoutSeconds()));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(aiProperties.baseUrl())
                .requestFactory(factory);
        if (aiProperties.apiKey() != null && !aiProperties.apiKey().isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + aiProperties.apiKey());
        }
        return builder.build();
    }
}
