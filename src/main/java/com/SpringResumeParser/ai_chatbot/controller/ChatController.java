package com.SpringResumeParser.ai_chatbot.controller;

import com.SpringResumeParser.ai_chatbot.service.ModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for handling AI chat requests.
 *
 * This controller demonstrates a flexible multi-provider AI architecture where:
 * - The AI provider (OpenAI, Gemini, etc.) can be selected per-request via headers
 * - The specific model can be overridden dynamically at runtime
 * - The same endpoint serves multiple AI backends transparently
 *
 * DESIGN PATTERN: Strategy Pattern
 * The controller delegates provider selection to ModelService, allowing
 * easy switching between AI providers without changing controller logic.
 *
 * @author HungryCoders
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    /**
     * Service responsible for resolving the appropriate ChatClient
     * based on the requested provider (openai, gemini, etc.)
     */
    @Autowired
    private ModelService modelService;

    /**
     * Main chat endpoint that processes user messages and returns AI responses.
     *
     * ENDPOINT: POST /api/chat
     *
     * REQUEST HEADERS:
     * - AI-Provider (optional): Specifies which AI provider to use.
     *                           Defaults to "openai" if not provided.
     *                           Supported values: "openai", "gemini"
     *
     * - AI-Model (optional): Overrides the default model for the provider.
     *                        Examples: "gpt-4o", "gpt-3.5-turbo", "gemini-1.5-pro"
     *                        If not provided, uses the default model configured in the bean.
     *
     * REQUEST BODY: Plain text message from the user
     *
     * RESPONSE: Plain text AI-generated response
     *
     * EXAMPLE USAGE:
     *
     * 1. Using default OpenAI with default model:
     *    curl -X POST http://localhost:8081/api/chat \
     *         -H "Content-Type: text/plain" \
     *         -d "What is Spring Boot?"
     *
     * 2. Using Gemini provider:
     *    curl -X POST http://localhost:8081/api/chat \
     *         -H "AI-Provider: gemini" \
     *         -H "Content-Type: text/plain" \
     *         -d "Explain microservices"
     *
     * 3. Using OpenAI with a specific model override:
     *    curl -X POST http://localhost:8081/api/chat \
     *         -H "AI-Provider: openai" \
     *         -H "AI-Model: gpt-4o" \
     *         -H "Content-Type: text/plain" \
     *         -d "Write a haiku about coding"
     *
     * @param provider The AI provider to use (from "AI-Provider" header, defaults to "openai")
     * @param model    Optional model override (from "AI-Model" header)
     * @param message  The user's message/prompt (from request body)
     * @return The AI-generated response as plain text
     */
    @PostMapping
    public String chat(
            @RequestHeader(value = "AI-Provider", defaultValue = "openai") String provider,
            @RequestHeader(value = "AI-Model", required = false) String model,
            @RequestBody String message
    ) {
        log.info("=== REQUEST RECEIVED ===");
        log.info("Provider: {}", provider);
        log.info("Model header: {}", model);
        log.info("Message: {}", message);

        // Validate: For non-OpenAI providers, model is mandatory
        if (!"openai".equalsIgnoreCase(provider) &&
                (model == null || model.isEmpty())) {
            return Map.of(
                    "error", true,
                    "message", "AI-Model header is required when using provider: " + provider
            ).toString();
        }

        // Step 1: Get the appropriate ChatClient based on provider
        // ModelService acts as a factory, returning the correct ChatClient bean
        ChatClient chatClient = modelService.getChatClient(provider);
        log.info("ChatClient class: {}", chatClient.getClass().getName());
        log.info("ChatClient: {}", chatClient);

        // Step 2: Check if a custom model override was requested
        if (model != null && !model.isEmpty()) {
            log.info("Using custom model override: {}", model);

            // SPRING AI FLUENT API EXPLAINED:
            //
            // chatClient.prompt()     → Starts building a prompt request
            //     .user(message)      → Sets the user's message (vs .system() for system prompts)
            //     .options(...)       → Overrides default options (model, temperature, etc.)
            //     .call()             → Executes the API call to the AI provider
            //     .content()          → Extracts just the text content from the response
            //
            // The .options() method allows runtime override of model settings
            // without changing the bean configuration. This is powerful for:
            // - A/B testing different models
            // - Letting users choose their preferred model
            // - Using cheaper models for simple queries, expensive ones for complex tasks

            return chatClient.prompt()
                    .user(message)
                    .options(OpenAiChatOptions.builder()
                            .model(model)          // Override the model (e.g., "gpt-4o")
                            .temperature(1.0)      // Creativity level (0.0-2.0)
                            .build())
                    .call()
                    .content();
        }

        log.info("Using default model from ChatClient bean");
        log.info("=== CALLING PROMPT ===");

        // Step 3: Use default configuration from the ChatClient bean
        // No .options() override means it uses whatever was configured
        // in MultiModelConfig (e.g., default OpenAI model or Gemini model)
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}