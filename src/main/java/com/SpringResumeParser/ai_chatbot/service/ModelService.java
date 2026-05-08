package com.SpringResumeParser.ai_chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Service for resolving the appropriate ChatClient based on provider name.
 *
 * KEY CONCEPT: Factory/Strategy Pattern for Multi-Provider AI
 *
 * This service acts as a FACTORY that returns the correct ChatClient
 * based on a string identifier. This enables:
 *
 * 1. RUNTIME PROVIDER SELECTION:
 *    Users can choose which AI provider to use per-request
 *    via headers or query parameters.
 *
 * 2. LOOSE COUPLING:
 *    Controllers don't need to know about specific ChatClient beans.
 *    They just ask for a provider by name.
 *
 * 3. EASY EXTENSIBILITY:
 *    Adding a new provider (Claude, Mistral, etc.) requires:
 *    - Adding a new bean in MultiModelConfig
 *    - Adding a case in the switch statement here
 *
 * DESIGN PATTERN: Strategy Pattern
 * The ChatClient interface is the "strategy" - different implementations
 * (OpenAI, Gemini) can be swapped at runtime based on the provider string.
 *
 * @author karthik
 */
@Service
public class ModelService {

    private static final Logger log = LoggerFactory.getLogger(ModelService.class);

    /**
     * ChatClient configured for OpenAI.
     *
     * KEY CONCEPT: @Qualifier for Bean Disambiguation
     *
     * When you have MULTIPLE beans of the same type (ChatClient),
     * Spring doesn't know which one to inject by default.
     *
     * @Qualifier("beanName") tells Spring exactly which bean to inject.
     * The name must match the @Bean("beanName") in MultiModelConfig.
     *
     * WITHOUT @Qualifier:
     * - Spring finds 2 ChatClient beans
     * - Throws: NoUniqueBeanDefinitionException
     *
     * ALTERNATIVES TO @QUALIFIER:
     * 1. @Primary on one bean (makes it the default)
     * 2. Field name matching bean name (openaiChatClient)
     * 3. Constructor injection with @Qualifier
     */
    @Autowired
    @Qualifier("openaiChatClient")
    private ChatClient openaiChatClient;

    /**
     * ChatClient configured for Google Gemini.
     *
     * This bean is created in MultiModelConfig using OpenAI-compatible
     * API configuration pointing to Gemini's endpoint.
     *
     * Demonstrates that Spring AI's OpenAI classes can work with
     * ANY provider that offers an OpenAI-compatible API.
     */
    @Autowired
    @Qualifier("geminiChatClient")
    private ChatClient geminiChatClient;

    /**
     * Resolves and returns the appropriate ChatClient for the given provider.
     *
     * FACTORY METHOD PATTERN:
     * Instead of controllers creating or choosing ChatClients directly,
     * they delegate to this method. Benefits:
     *
     * - Single point of change for provider logic
     * - Easy to add logging, metrics, or validation
     * - Controllers remain clean and focused on HTTP concerns
     *
     * SUPPORTED PROVIDERS:
     * - "openai" → OpenAI GPT models (GPT-4, GPT-4o, etc.)
     * - "gemini" → Google Gemini models (gemini-1.5-flash, gemini-1.5-pro)
     * - null/empty → Defaults to OpenAI
     *
     * USAGE IN CONTROLLERS:
     * ```java
     * // From header: AI-Provider: gemini
     * ChatClient client = modelService.getChatClient(provider);
     * String response = client.prompt().user(message).call().content();
     * ```
     *
     * @param provider The provider identifier (case-insensitive)
     * @return The appropriate ChatClient for the requested provider
     * @throws IllegalArgumentException if provider is not supported
     */
    public ChatClient getChatClient(String provider) {
        log.info("getChatClient called with provider: {}", provider);
        log.info("openaiChatClient instance: {}", openaiChatClient);
        log.info("geminiChatClient instance: {}", geminiChatClient);

        // ==================== DEFAULT HANDLING ====================
        // If no provider specified, default to OpenAI
        // This provides a sensible fallback and backwards compatibility
        if (provider == null || provider.isEmpty()) {
            log.info("Returning default openaiChatClient");
            return openaiChatClient;
        }

        // ==================== PROVIDER RESOLUTION ====================
        // JAVA 14+ SWITCH EXPRESSION with pattern matching
        //
        // Benefits over traditional switch:
        // - More concise syntax
        // - No fall-through bugs (no break needed)
        // - Can return values directly
        // - Exhaustiveness checking by compiler
        //
        // NOTE: .toLowerCase() ensures case-insensitive matching
        // "OpenAI", "OPENAI", "openai" all work the same
        return switch (provider.toLowerCase()) {
            case "openai" -> {
                log.info("Returning openaiChatClient");
                yield openaiChatClient;  // 'yield' returns value from switch expression block
            }
            case "gemini" -> {
                log.info("Returning geminiChatClient");
                yield geminiChatClient;
            }
            default -> throw new IllegalArgumentException(
                    "Unknown provider: " + provider + ". Supported: openai, gemini"
            );
            // FAIL FAST: Unknown providers throw immediately with clear message
            // This is better than returning null (which causes NullPointerException later)
        };
    }
}