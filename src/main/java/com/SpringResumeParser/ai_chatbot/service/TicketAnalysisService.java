package com.SpringResumeParser.ai_chatbot.service;

import com.SpringResumeParser.ai_chatbot.model.SuggestedResponse;
import com.SpringResumeParser.ai_chatbot.model.TicketAnalysis;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Service for AI-powered ticket analysis and response generation.
 *
 * KEY CONCEPT: Structured Output with .entity()
 *
 * This service demonstrates one of the most powerful features in Spring AI:
 * automatically converting AI responses to Java objects using .entity().
 *
 * Instead of:
 *   String response = chatClient.prompt().call().content();
 *   // Then manually parse JSON...
 *
 * We use:
 *   TicketAnalysis result = chatClient.prompt().call().entity(TicketAnalysis.class);
 *   // Spring AI handles JSON parsing automatically!
 *
 * HOW .entity() WORKS:
 * 1. Spring AI adds instructions to the prompt telling AI to respond in JSON
 * 2. AI generates a JSON response matching the class structure
 * 3. Spring AI deserializes the JSON into the specified Java class
 * 4. You get a ready-to-use Java object
 *
 * REQUIREMENTS FOR .entity() TO WORK:
 * - Target class must have no-args constructor
 * - Field names should match expected JSON keys
 * - Enums must match exact string values from AI
 *
 * @author Karthik
 */
@Service
public class TicketAnalysisService {
    /**
     * Service for resolving the appropriate ChatClient based on provider.
     */
    private final ModelService modelService;

    /**
     * Spring's ResourceLoader for accessing template files from classpath.
     */
    private final ResourceLoader resourceLoader;

    /**
     * Constructor injection for dependencies.
     * Preferred over @Autowired for testability and immutability.
     */
    public TicketAnalysisService(ModelService modelService, ResourceLoader resourceLoader) {
        this.modelService = modelService;
        this.resourceLoader = resourceLoader;
    }

    /**
     * Analyzes a support ticket and returns structured analysis.
     *
     * KEY CONCEPT: .entity(Class) for Single Object
     *
     * The .entity(TicketAnalysis.class) method tells Spring AI to:
     * 1. Instruct the AI to respond in JSON format
     * 2. Parse the JSON response
     * 3. Convert it to a TicketAnalysis object
     *
     * EXAMPLE FLOW:
     *
     *   Input: "My order #123 is broken and I want a refund NOW!"
     *          ↓
     *   Prompt Template fills in {ticketText}
     *          ↓
     *   AI analyzes and returns JSON:
     *   {
     *     "category": "Order Issues",
     *     "priority": "HIGH",
     *     "sentiment": "ANGRY",
     *     ...
     *   }
     *          ↓
     *   .entity() converts to TicketAnalysis object
     *          ↓
     *   Return: TicketAnalysis { priority=HIGH, sentiment=ANGRY, ... }
     *
     * @param ticketText Raw text content of the support ticket
     * @param provider AI provider to use (openai, gemini)
     * @param model Specific model to use
     * @return TicketAnalysis object with structured analysis results
     */
    public TicketAnalysis analyzeTicket(String ticketText,
                                        String provider,
                                        String model)
    {
        // Get the appropriate ChatClient for the provider
        ChatClient chatClient = modelService.getChatClient(provider);

        // Create prompt from template with ticket text
        Prompt prompt = createTicketAnalysisPrompt(ticketText);

        // Execute AI call and convert response to TicketAnalysis object
        //
        // SPRING AI MAGIC:
        // .prompt(prompt)  → Sets the prompt to send
        // .options(...)    → Configures model settings
        // .call()          → Executes the API call
        // .entity(Class)   → Converts JSON response to Java object
        return chatClient
                .prompt(prompt)
                .options(OpenAiChatOptions.builder()
                        .model(model)
                        .build())
                .call()
                .entity(TicketAnalysis.class);

    }

    /**
     * Generates suggested responses for urgent/critical tickets.
     *
     * KEY CONCEPT: .entity(ParameterizedTypeReference) for Collections
     *
     * When the AI returns a LIST of objects (not a single object),
     * we need to use ParameterizedTypeReference to preserve generic type info.
     *
     * WHY ParameterizedTypeReference?
     *
     * Java erases generic types at runtime (type erasure), so:
     *   .entity(List<SuggestedResponse>.class)  // Won't compile!
     *   .entity(List.class)                      // Loses type info!
     *
     * ParameterizedTypeReference is a workaround that captures the full
     * generic type (List<SuggestedResponse>) at compile time.
     *
     * EXAMPLE FLOW:
     *
     *   Input: TicketAnalysis { category="Billing", issues="Overcharged" }
     *          ↓
     *   Prompt Template fills in {category} and {issues}
     *          ↓
     *   AI generates array of suggested responses:
     *   [
     *     { "tone": "empathetic", "message": "I sincerely apologize..." },
     *     { "tone": "solution", "message": "I've issued a refund..." },
     *     { "tone": "followup", "message": "Is there anything else..." }
     *   ]
     *          ↓
     *   .entity() converts to List<SuggestedResponse>
     *
     * @param analysis The ticket analysis containing category and issues
     * @param provider AI provider to use
     * @param model Specific model to use
     * @return List of suggested response messages for the agent
     */
    public List<SuggestedResponse> generateUrgentResponses(
            TicketAnalysis analysis, String provider, String model)
    {
        ChatClient chatClient = modelService.getChatClient(provider);

        // Create prompt with analysis details
        Prompt prompt = createTicketAnalysisResponsesPrompt(analysis);

        // Execute AI call and convert response to List<SuggestedResponse>
        //
        // NOTE: Using ParameterizedTypeReference for generic List type
        // This is required because Java type erasure would otherwise
        // lose the SuggestedResponse type information at runtime.
        return chatClient
                .prompt(prompt)
                .options(OpenAiChatOptions.builder()
                        .model(model)
                        .build())
                .call()
                .entity(new ParameterizedTypeReference<List<SuggestedResponse>>() {});

    }


    /**
     * Creates a prompt for ticket analysis using a template.
     *
     * TEMPLATE APPROACH:
     * Instead of building prompts in Java code, we use external template files.
     * This separates prompt engineering from application logic.
     *
     * The template (ticket-analysis.txt) contains:
     * - System instructions for the AI
     * - Output format specification (JSON structure)
     * - The {ticketText} placeholder for dynamic content
     *
     * @param ticketText The raw ticket text to analyze
     * @return Prompt object ready to send to AI
     */
    private Prompt createTicketAnalysisPrompt(String ticketText) {

        // Load template from classpath
        // Location: src/main/resources/templates/ticket-analysis.txt
        String templateContent = loadTemplate("classpath:templates/ticket-analysis.txt");

        // Create Spring AI PromptTemplate
        // PromptTemplate handles {placeholder} substitution
        PromptTemplate promptTemplate = new PromptTemplate(templateContent);

        // Prepare variables - keys must match {placeholders} in template
        // Template has {ticketText} → we provide "ticketText" key
        Map<String, Object> variables = Map.of(
                "ticketText", ticketText
        );

        // Fill template and return Prompt object
        return promptTemplate.create(variables);
    }

    /**
     * Creates a prompt for generating suggested responses.
     *
     * Uses a separate template (ticket-analysis-responses.txt) that
     * takes the analysis results and generates helpful responses.
     *
     * The template uses {category} and {issues} to generate
     * contextually appropriate response suggestions.
     *
     * @param analysis The completed ticket analysis
     * @return Prompt object for generating responses
     */
    private Prompt createTicketAnalysisResponsesPrompt(TicketAnalysis analysis) {

        // Load template from classpath
        // Location: src/main/resources/templates/ticket-analysis-responses.txt
        String templateContent = loadTemplate("classpath:templates/ticket-analysis-responses.txt");

        // Create Spring AI PromptTemplate
        PromptTemplate promptTemplate = new PromptTemplate(templateContent);

        // Prepare variables from the analysis object
        // Extract specific fields needed by the template
        Map<String, Object> variables = Map.of(
                "category", analysis.getCategory(),
                "issues", analysis.getKeyIssues()
        );

        // Fill template and return Prompt object
        return promptTemplate.create(variables);
    }

    /**
     * Loads a template file from the classpath.
     *
     * TEMPLATE LOCATION: src/main/resources/templates/
     *
     * Using ResourceLoader allows loading files from:
     * - classpath: (inside JAR/resources folder)
     * - file: (filesystem path)
     * - http: (remote URL)
     *
     * @param location Resource location (e.g., "classpath:templates/file.txt")
     * @return Template content as String
     * @throws RuntimeException if template cannot be loaded
     */
    private String loadTemplate(String location) {
        try {
            Resource resource = resourceLoader.getResource(
                    location
            );
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load ticket analysis template", e);
        }
    }

    /**
     * Maps priority levels to hex color codes for UI display.
     *
     * Used by the controller to return visual indicators
     * that the frontend can use for color-coded displays.
     *
     * COLOR MEANINGS:
     * - Red (#dc3545): CRITICAL - Immediate attention required
     * - Orange (#fd7e14): HIGH - Priority handling needed
     * - Yellow (#ffc107): MEDIUM - Standard processing
     * - Green (#28a745): LOW - No urgency
     *
     * @param priority The ticket priority level
     * @return Hex color code string
     */
    public String getPriorityColor(TicketAnalysis.Priority priority) {
        return switch (priority) {
            case CRITICAL -> "#dc3545"; // Red
            case HIGH -> "#fd7e14";     // Orange
            case MEDIUM -> "#ffc107";   // Yellow
            case LOW -> "#28a745";      // Green
        };
    }

    /**
     * Maps sentiment values to emoji for quick visual feedback.
     *
     * Emojis provide instant visual recognition of customer mood,
     * allowing support agents to quickly scan ticket queues.
     *
     * @param sentiment The detected customer sentiment
     * @return Emoji character representing the sentiment
     */
    public String getSentimentEmoji(TicketAnalysis.Sentiment sentiment) {
        return switch (sentiment) {
            case HAPPY -> "😊";
            case NEUTRAL -> "😐";
            case FRUSTRATED -> "😤";
            case ANGRY -> "😡";
        };
    }


}