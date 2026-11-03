
package com.eventhub.eventhub_backend.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class GeminiAiClient implements AiClient {

    private final String apiKey;
    private final String model;
    private final RestClient restClient;

    public GeminiAiClient(String apiKey, String model,
                          String baseUrl, int timeoutSeconds) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    @Override
    public Map<String, Object> callTool(
            String systemPrompt,
            String userMessage,
            String toolName,
            String toolDescription,
            Map<String, Object> inputSchema,
            int maxTokens) {

        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", toolName);
        function.put("description", toolDescription);
        function.put("parameters", inputSchema);

        Map<String, Object> tool = Map.of(
                "functionDeclarations", List.of(function));

        Map<String, Object> body = baseBody(
                systemPrompt, userMessage, maxTokens);

        body.put("tools", List.of(tool));
        body.put("toolConfig", Map.of(
                "functionCallingConfig", Map.of(
                        "mode", "ANY",
                        "allowedFunctionNames", List.of(toolName)
                )
        ));

        GeminiResponse response = send(body);

        return response.candidates().stream()
                .filter(candidate -> candidate.content() != null)
                .flatMap(candidate -> candidate.content().parts().stream())
                .filter(part -> part.functionCall() != null)
                .filter(part -> toolName.equals(part.functionCall().name()))
                .map(part -> part.functionCall().args())
                .filter(args -> args != null)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "The AI returned an unusable answer. Please try again"));
    }

    @Override
    public String complete(
            String systemPrompt,
            String userMessage,
            int maxTokens) {

        GeminiResponse response = send(
                baseBody(systemPrompt, userMessage, maxTokens));

        String result = response.candidates().stream()
                .filter(candidate -> candidate.content() != null)
                .flatMap(candidate -> candidate.content().parts().stream())
                .map(Part::text)
                .filter(text -> text != null)
                .collect(Collectors.joining("\n"))
                .trim();

        if (result.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "The AI returned an empty answer. Please try again");
        }

        return result;
    }

    private Map<String, Object> baseBody(
            String systemPrompt,
            String userMessage,
            int maxTokens) {

        Map<String, Object> body = new LinkedHashMap<>();

        String prompt = systemPrompt + "\n\n" + userMessage;

        body.put("contents", List.of(
                Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt))
                )
        ));

        body.put("generationConfig", Map.of(
                "maxOutputTokens", maxTokens
        ));

        return body;
    }

    private GeminiResponse send(Map<String, Object> body) {

        if (!isConfigured()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI features are not configured on this server");
        }

        try {
            GeminiResponse response = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GeminiResponse.class);

            if (response == null || response.candidates() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "The AI service returned an empty answer");
            }

            if (response.usage() != null) {
                log.info("Gemini AI call: model={} inputTokens={} outputTokens={}",
                        model,
                        response.usage().inputTokens(),
                        response.usage().outputTokens());
            }

            return response;

//        } catch (RestClientResponseException e) {
//            log.warn("Gemini API answered with HTTP {}",
//                    e.getStatusCode().value());
//            throw mapFailure(e.getStatusCode().value());
//
//        } catch (ResourceAccessException e) {
//            log.warn("Gemini service not reachable or too slow: {}",
//                    e.getMessage());
//
//            throw new ResponseStatusException(
//                    HttpStatus.GATEWAY_TIMEOUT,
//                    "The AI service took too long to answer. Please try again");
//        }
        } catch (RestClientResponseException e) {
            String errorBody = e.getResponseBodyAsString();

            log.warn("Gemini API answered with HTTP {}. Error: {}",
                    e.getStatusCode().value(),
                    errorBody.length() > 1000
                            ? errorBody.substring(0, 1000)
                            : errorBody);

            throw mapFailure(e.getStatusCode().value());

        } catch (ResourceAccessException e) {
            log.warn("Gemini service not reachable or too slow: {}",
                    e.getMessage());

            throw new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "The AI service took too long to answer. Please try again");
        }
    }

    private ResponseStatusException mapFailure(int status) {
        return switch (status) {
            case 401, 403 -> new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The Gemini service rejected this API key");

            case 404 -> new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The configured Gemini model was not found");

            case 429 -> new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Gemini usage limit reached. Please try again later");

            default -> new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "The Gemini service had a problem. Please try again");
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiResponse(
            List<Candidate> candidates,
            @JsonProperty("usageMetadata") Usage usage) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Candidate(Content content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Content(List<Part> parts) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Part(
            String text,
            @JsonProperty("functionCall") FunctionCall functionCall) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FunctionCall(
            String name,
            Map<String, Object> args) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(
            @JsonProperty("promptTokenCount") Integer inputTokens,
            @JsonProperty("candidatesTokenCount") Integer outputTokens) {}
}
