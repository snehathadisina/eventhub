package com.eventhub.eventhub_backend.ai;

import java.util.Map;

public interface AiClient {

    boolean isConfigured();

    /**
     * Asks the model to answer by calling a "tool", so the answer is JSON matching the schema
     * instead of free text we would have to parse.
     */
    Map<String, Object> callTool(String systemPrompt, String userMessage, String toolName,
                                 String toolDescription, Map<String, Object> inputSchema, int maxTokens);

    /** A plain text answer. */
    String complete(String systemPrompt, String userMessage, int maxTokens);
}