package com.SpringResumeParser.ai_chatbot.controller;

import com.SpringResumeParser.ai_chatbot.service.ModelService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * REST Controller for streaming AI chat responses.
 *
 * KEY CONCEPT: Streaming vs Blocking Responses
 *
 * BLOCKING (Regular) Approach:
 * - Client sends request → Waits → Gets complete response after AI finishes
 * - User sees nothing until the entire response is generated
 * - Can feel slow for long responses (10+ seconds of waiting)
 *
 * STREAMING Approach:
 * - Client sends request → Receives tokens as they're generated
 * - User sees words appearing in real-time (like ChatGPT's typing effect)
 * - Much better UX for conversational AI applications
 *
 * TECHNOLOGY STACK:
 * - Server-Sent Events (SSE): HTTP-based protocol for server→client streaming
 * - Project Reactor: Spring's reactive library (Flux, Mono)
 * - Flux<String>: A reactive stream that emits multiple String values over time
 *
 * HOW IT WORKS UNDER THE HOOD:
 * 1. Client opens HTTP connection with Accept: text/event-stream
 * 2. Server keeps connection open
 * 3. AI generates tokens one-by-one → Server pushes each token immediately
 * 4. Client receives and displays tokens in real-time
 * 5. Connection closes when stream completes
 *
 * @author HungryCoders
 */
@RestController
@RequestMapping("/api")
public class StreamController {

    private static final Logger log = LoggerFactory.getLogger(StreamController.class);

    /**
     * Service for resolving the appropriate ChatClient based on provider.
     */
    @Autowired
    private ModelService modelService;

    /**
     * Stream AI responses in real-time using Server-Sent Events (SSE).
     *
     * ENDPOINT: GET /api/stream
     *
     * WHY GET INSTEAD OF POST?
     * - SSE traditionally works best with GET requests
     * - Easier to test in browser (just visit the URL)
     * - For complex payloads, POST with SSE is also possible but less common
     *
     * QUERY PARAMETERS:
     * - message (required): The user's prompt/question
     * - provider (optional): AI provider to use. Defaults to "openai"
     * - model (optional): Override the default model (e.g., "gpt-4o")
     *
     * RESPONSE:
     * - Content-Type: text/event-stream
     * - Body: Stream of text chunks as they're generated
     *
     * EXAMPLE USAGE:
     *
     * 1. Browser (simple test):
     *    http://localhost:8081/api/stream?message=Tell%20me%20a%20joke
     *
     * 2. curl with streaming:
     *    curl -N "http://localhost:8081/api/stream?message=Explain%20Java%20streams&provider=openai"
     *
     *    The -N flag disables buffering to see real-time output
     *
     * 3. JavaScript (EventSource API):
     *    const eventSource = new EventSource('/api/stream?message=Hello');
     *    eventSource.onmessage = (event) => {
     *        document.getElementById('output').textContent += event.data;
     *    };
     *
     * 4. JavaScript (fetch with ReadableStream):
     *    const response = await fetch('/api/stream?message=Hello');
     *    const reader = response.body.getReader();
     *    while (true) {
     *        const { done, value } = await reader.read();
     *        if (done) break;
     *        console.log(new TextDecoder().decode(value));
     *    }
     *
     * @param message  The user's prompt (query param)
     * @param provider AI provider to use (query param, defaults to "openai")
     * @param model    Optional model override (query param)
     * @return Flux<String> - A reactive stream of text chunks
     */
    @GetMapping(value = "/stream", produces = "text/event-stream")
    public Flux<String> streamChat(
            @RequestParam String message,
            @RequestParam(defaultValue = "openai") String provider,
            @RequestParam(required = false) String model
    ) {
        log.info("=== REQUEST RECEIVED ===");
        log.info("Provider: {}", provider);
        log.info("Model header: {}", model);
        log.info("Message: {}", message);

        // Validate: For non-OpenAI providers, model is mandatory
        if (!"openai".equalsIgnoreCase(provider) &&
                (model == null || model.isEmpty())) {
            return Flux.just("Error: AI-Model parameter is required when using provider: " + provider);
        }

        // Get the appropriate ChatClient for the requested provider
        ChatClient chatClient = modelService.getChatClient(provider);
        log.info("ChatClient class: {}", chatClient.getClass().getName());
        log.info("ChatClient: {}", chatClient);

        // ==================== STREAMING WITH MODEL OVERRIDE ====================
        if (model != null && !model.isEmpty()) {
            log.info("Using custom model override: {}", model);

            // KEY CONCEPT: .stream() vs .call()
            //
            // BLOCKING:  .call().content()    → Returns String (waits for complete response)
            // STREAMING: .stream().content()  → Returns Flux<String> (emits chunks as they arrive)
            //
            // The Flux<String> is a "cold" stream - it doesn't start until someone subscribes.
            // Spring WebFlux automatically subscribes when the HTTP response starts.
            //
            // Each emission in the Flux is typically:
            // - A single token (word or word-piece)
            // - Or a small chunk of text
            //
            // The AI provider's API sends these chunks, and Spring AI
            // transforms them into a reactive Flux for easy consumption.

            return chatClient.prompt()
                    .user(message)
                    .options(OpenAiChatOptions.builder()
                            .model(model)
                            .temperature(1.0)
                            .build())
                    .stream()      // ← Returns a streaming response builder
                    .content();    // ← Extracts just the text content as Flux<String>
        }

        log.info("Using default model from ChatClient bean");
        log.info("=== CALLING PROMPT ===");

        // ==================== STREAMING WITH DEFAULT CONFIG ====================
        // Uses whatever model was configured in the ChatClient bean (MultiModelConfig)
        return chatClient.prompt()
                .user(message)
                .stream()
                .content();
    }
}