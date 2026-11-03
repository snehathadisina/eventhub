
package com.eventhub.eventhub_backend.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    AiClient aiClient(
            @Value("${app.ai.api-key:}") String apiKey,
            @Value("${app.ai.model:gemini-2.5-flash-lite}") String model,
            @Value("${app.ai.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${app.ai.timeout-seconds:40}") int timeoutSeconds) {

        return new GeminiAiClient(apiKey, model, baseUrl, timeoutSeconds);
    }
}
