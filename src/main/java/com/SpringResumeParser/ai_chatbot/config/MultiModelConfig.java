package com.SpringResumeParser.ai_chatbot.config;

import com.SpringResumeParser.ai_chatbot.advisor.PiiRedactionAdvisor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration class for setting up multiple AI model clients in Spring AI.
 *
 * This class demonstrates a powerful pattern: using Spring AI's OpenAI integration
 * to connect to MULTIPLE AI providers (OpenAI and Google Gemini) within the same application.
 *
 * KEY CONCEPT: Many AI providers (including Google Gemini) offer OpenAI-compatible APIs.
 * This means we can use Spring AI's OpenAI classes to connect to them by simply
 * changing the base URL and API key.
 *
 * @author Karthik
 */
@Configuration
public class MultiModelConfig {

    // Logger for debugging and monitoring bean creation
    private static final Logger log = LoggerFactory.getLogger(MultiModelConfig.class);

    // ==================== GEMINI CONFIGURATION PROPERTIES ====================
    // These values are injected from application.properties/application.yml
    // Using @Value allows externalized configuration - no hardcoded secrets!

    /**
     * API key for authenticating with Google Gemini.
     * Should be stored securely (environment variables or secrets manager in production).
     */
    @Value("${gemini.api.key}")
    private String geminiKey;

    /**
     * Base URL for Gemini's OpenAI-compatible API endpoint.
     * Example: https://generativelanguage.googleapis.com/v1beta/openai
     */
    @Value("${gemini.api.url}")
    private String geminiUrl;

    /**
     * The path for chat completions endpoint.
     * Gemini uses a different path than OpenAI's default "/v1/chat/completions".
     */
    @Value("${gemini.api.completions.path}")
    private String completionsPath;

    /**
     * The Gemini model name to use (e.g., "gemini-1.5-flash", "gemini-1.5-pro").
     */
    @Value("${gemini.model.name}")
    private String geminiModelName;

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(10)  // Keep last 10 messages
                .build();
    }

    // ==================== OPENAI CHAT CLIENT BEAN ====================

    /**
     * Creates the primary ChatClient bean for OpenAI.
     *
     * KEY CONCEPTS:
     * 1. @Bean("openaiChatClient") - Creates a named bean, allowing us to have multiple
     *    ChatClient beans with different names (qualifier-based injection).
     *
     * 2. @Primary - Marks this as the DEFAULT ChatClient. When you inject ChatClient
     *    without specifying a qualifier, Spring will use this one.
     *
     * 3. OpenAiChatModel is AUTO-CONFIGURED by Spring Boot when you include
     *    spring-ai-openai-spring-boot-starter and provide spring.ai.openai.api-key
     *    in your properties. We just inject it here!
     *
     * 4. ChatClient is the high-level abstraction in Spring AI for interacting with
     *    any chat model. It provides a fluent API for sending prompts and receiving responses.
     *
     * @param openAiChatModel Auto-configured OpenAI chat model from Spring Boot
     * @return ChatClient configured for OpenAI
     */
    @Bean("openaiChatClient")
    @Primary
    public ChatClient openaiChatClient(OpenAiChatModel openAiChatModel,
                                       ChatMemory chatMemory) {
        log.info("=== Creating openaiChatClient bean ===");
        log.info("Using auto-configured OpenAiChatModel: {}", openAiChatModel);

        // ChatClient.create() wraps the model in a client with fluent API
       // ChatClient client = ChatClient.create(openAiChatModel);

        ChatClient.Builder builder = ChatClient.builder(openAiChatModel);
        builder.defaultAdvisors(
                // ADVISOR 1: PII Redaction (Security)
                // Removes sensitive data BEFORE it reaches logging or AI.
                // Must run first to ensure no PII leaks anywhere.
                new PiiRedactionAdvisor(),

                // ADVISOR 2: Simple Logger (Debugging/Monitoring)
                // Logs requests and responses for debugging.
                // Runs after PII redaction, so logs are safe.
                new SimpleLoggerAdvisor(),

                // ADVISOR 3: Chat Memory (Conversation Context)
                // Automatically manages conversation history.
                // - On request: Adds previous messages to the prompt
                // - On response: Stores the new exchange in memory
                // Uses the injected chatMemory bean for storage.
                MessageChatMemoryAdvisor.builder(chatMemory).build()
        );

        // ==================== BUILD THE CHATCLIENT ====================
        //
        // .build() creates the final immutable ChatClient instance.
        // This client now has all advisors configured and ready.
        //
        // Every call made through this client will automatically:
        // 1. Redact PII from user messages
        // 2. Log requests and responses
        // 3. Maintain conversation memory
        ChatClient client = builder.build();

        log.info("Created openaiChatClient: {}", client);
        return client;
    }

    // ==================== GEMINI CHAT CLIENT BEAN ====================

    /**
     * Creates a ChatClient bean for Google Gemini using OpenAI-compatible API.
     *
     * KEY CONCEPTS:
     * 1. OPENAI COMPATIBILITY: Google Gemini provides an OpenAI-compatible endpoint,
     *    allowing us to reuse Spring AI's OpenAI classes instead of waiting for
     *    a dedicated Gemini integration.
     *
     * 2. MANUAL CONFIGURATION: Unlike OpenAI (which is auto-configured), we manually
     *    build the API client, model, and ChatClient for Gemini.
     *
     * 3. BUILDER PATTERN: Spring AI uses builders extensively for configuration,
     *    making it easy to customize API endpoints, model options, etc.
     *
     * HOW IT WORKS:
     * - OpenAiApi: Low-level HTTP client that talks to the API endpoint
     * - OpenAiChatModel: Wraps the API and adds model-specific logic
     * - ChatClient: High-level abstraction for chat interactions
     *
     * @return ChatClient configured for Google Gemini
     */
    @Bean("geminiChatClient")
    public ChatClient geminiChatClient(ChatMemory chatMemory) {
        log.info("=== Creating geminiChatClient bean ===");
        log.info("Gemini URL: {}", geminiUrl);
        log.info("Gemini completionsPath: {}", completionsPath);
        log.info("Gemini model: {}", geminiModelName);

        // Step 1: Create the low-level API client pointing to Gemini's endpoint
        // We override baseUrl and completionsPath to match Gemini's API structure
        OpenAiApi geminiApi = OpenAiApi.builder()
                .baseUrl(geminiUrl)                    // Gemini's base URL instead of api.openai.com
                .completionsPath(completionsPath)      // Gemini's completions path
                .apiKey(geminiKey)                     // Gemini API key
                .build();
        log.info("Created geminiApi: {}", geminiApi);

        // Step 2: Create the chat model with Gemini-specific options
        OpenAiChatModel geminiModel = OpenAiChatModel.builder()
                .openAiApi(geminiApi)                  // Use our custom Gemini API client
                .defaultOptions(OpenAiChatOptions.builder()
                        .model(geminiModelName)        // Specify which Gemini model to use
                        .temperature(1.0)              // Controls randomness (0.0 = deterministic, 2.0 = very random)
                        .build())
                .build();
        log.info("Created geminiModel: {}", geminiModel);

        // Step 3: Wrap the model in a ChatClient for easy interaction
      //  ChatClient client = ChatClient.create(geminiModel);
        ChatClient.Builder builder = ChatClient.builder(geminiModel);
        builder.defaultAdvisors(
                // ADVISOR 1: PII Redaction (Security)
                // Removes sensitive data BEFORE it reaches logging or AI.
                // Must run first to ensure no PII leaks anywhere.
                new PiiRedactionAdvisor(),

                // ADVISOR 2: Simple Logger (Debugging/Monitoring)
                // Logs requests and responses for debugging.
                // Runs after PII redaction, so logs are safe.
                new SimpleLoggerAdvisor(),

                // ADVISOR 3: Chat Memory (Conversation Context)
                // Automatically manages conversation history.
                // - On request: Adds previous messages to the prompt
                // - On response: Stores the new exchange in memory
                // Uses the injected chatMemory bean for storage.
                MessageChatMemoryAdvisor.builder(chatMemory).build()
        );

        // ==================== BUILD THE CHATCLIENT ====================
        //
        // .build() creates the final immutable ChatClient instance.
        // This client now has all advisors configured and ready.
        //
        // Every call made through this client will automatically:
        // 1. Redact PII from user messages
        // 2. Log requests and responses
        // 3. Maintain conversation memory
        ChatClient client = builder.build();
        log.info("Created geminiChatClient: {}", client);
        return client;
    }
}