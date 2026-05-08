package com.SpringResumeParser.ai_chatbot.controller;

import com.SpringResumeParser.ai_chatbot.model.SuggestedResponse;
import com.SpringResumeParser.ai_chatbot.model.TicketAnalysis;
import com.SpringResumeParser.ai_chatbot.service.TicketAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for AI-powered support ticket analysis.
 *
 * KEY CONCEPT: Structured Output from LLMs
 *
 * This controller demonstrates how to get STRUCTURED DATA (Java objects)
 * from AI responses instead of plain text. This is crucial for:
 * - Automated ticket routing based on priority/category
 * - Sentiment-based escalation workflows
 * - Integration with existing ticketing systems
 *
 * USE CASE: Customer Support Automation
 *
 * Support teams handle 100s of tickets daily. This AI-powered system:
 * 1. Analyzes ticket text to extract priority, sentiment, and category
 * 2. Automatically routes tickets to appropriate teams
 * 3. Generates suggested responses for critical situations
 * 4. Provides visual indicators (colors, emojis) for quick triage
 *
 * WORKFLOW:
 *
 *    Ticket Text                AI Analysis              Structured Output
 *    ───────────                ───────────              ─────────────────
 *    "My order is              Prompt Template    →     TicketAnalysis {
 *    broken and I'm             + LLM Call               priority: CRITICAL
 *    very upset!"                                        sentiment: ANGRY
 *                                                        category: ORDER_ISSUE
 *                                                       }
 *
 * @author HungryCoders
 */
@RestController
@RequestMapping("/api/tickets")
public class TicketAnalysisController {

    /**
     * Service that handles ticket analysis and response generation.
     * Contains the prompt templates and AI interaction logic.
     */
    @Autowired
    private TicketAnalysisService ticketAnalysisService;

    // ==================== BUSINESS CONTEXT ====================
    //
    // This endpoint solves real problems for support teams:
    // - Manually reading and prioritizing 100s of tickets is slow
    // - Human bias can affect priority assignment
    // - Critical issues might get buried in the queue
    // - Angry customers need immediate attention
    //
    // The AI helps by:
    // - Automatically setting priority based on content
    // - Routing tickets to appropriate departments
    // - Detecting customer sentiment for escalation
    // - Suggesting responses for urgent situations

    /**
     * Analyzes a support ticket using AI and returns structured analysis.
     *
     * ENDPOINT: POST /api/tickets/analyze
     *
     * KEY CONCEPT: AI → Java Object Conversion
     *
     * Instead of getting plain text from the AI, we use prompt engineering
     * to get JSON responses, then convert them to Java objects:
     *
     *    AI Response (JSON)              Java Object
     *    ──────────────────              ───────────
     *    {                        →      TicketAnalysis {
     *      "priority": "CRITICAL",         Priority.CRITICAL,
     *      "sentiment": "ANGRY",           Sentiment.ANGRY,
     *      "category": "ORDER_ISSUE"       Category.ORDER_ISSUE
     *    }                               }
     *
     * This allows us to use the AI output in business logic (if/else, routing, etc.)
     *
     * QUERY PARAMETERS:
     * - provider (optional): AI provider to use. Defaults to "openai"
     * - model (optional): Model to use. Defaults to "gpt-5"
     *
     * REQUEST BODY (JSON):
     * {
     *     "ticketText": "My order #12345 arrived damaged. Very disappointed!"
     * }
     *
     * RESPONSE (JSON):
     * {
     *     "success": true,
     *     "analysis": {
     *         "priority": "HIGH",
     *         "sentiment": "FRUSTRATED",
     *         "category": "ORDER_ISSUE",
     *         "summary": "Customer received damaged order",
     *         "suggestedDepartment": "Fulfillment"
     *     },
     *     "priorityColor": "#FFA500",
     *     "sentimentEmoji": "😤"
     * }
     *
     * ESCALATION FLOW:
     * If priority is CRITICAL AND sentiment is ANGRY:
     * - Additional AI call generates suggested urgent responses
     * - Response includes pre-written messages for support agent
     * - UI can trigger escalation alerts
     *
     * @param request Map containing "ticketText" key with the ticket content
     * @param provider AI provider to use (openai, gemini)
     * @param model Specific model to use
     * @return Analysis results with priority color and sentiment emoji
     */
    @PostMapping("/analyze")
    public Map<String, Object> analyzeTicket(
            @RequestBody Map<String, String> request,
            @RequestParam(defaultValue = "openai") String provider,
            @RequestParam(defaultValue = "gpt-5") String model
    )
    {
        // Extract the ticket text from request body
        String ticketText = request.get("ticketText");

        try {
            // ==================== STEP 1: ANALYZE TICKET ====================
            //
            // The service sends the ticket text to the AI with a prompt template
            // that instructs the AI to respond in JSON format.
            //
            // Spring AI can automatically convert the JSON response to a Java object
            // using BeanOutputConverter or similar mechanisms.
            //
            // The returned TicketAnalysis object contains:
            // - priority: CRITICAL, HIGH, MEDIUM, LOW
            // - sentiment: ANGRY, FRUSTRATED, NEUTRAL, SATISFIED
            // - category: ORDER_ISSUE, BILLING, TECHNICAL, etc.
            // - summary: Brief description of the issue
            // - suggestedDepartment: Where to route the ticket
            TicketAnalysis analysis = ticketAnalysisService.analyzeTicket(
                    ticketText,
                    provider,
                    model
            );

            System.out.println(analysis.getPriority());

            // ==================== STEP 2: CHECK FOR CRITICAL SITUATION ====================
            //
            // KEY CONCEPT: Business Logic on AI Output
            //
            // Because we have structured data (Java enums), we can use standard
            // Java logic to make decisions. This would be impossible with plain text!
            //
            // ESCALATION CRITERIA:
            // - Priority is CRITICAL (system down, major issue)
            // - AND customer is ANGRY (high churn risk)
            //
            // This combination requires immediate attention and pre-written responses
            // to help the support agent de-escalate quickly.
            if (analysis.getPriority().equals(TicketAnalysis.Priority.CRITICAL)
                    && analysis.getSentiment().equals(TicketAnalysis.Sentiment.ANGRY)) {

                // ==================== STEP 2A: GENERATE URGENT RESPONSES ====================
                //
                // For critical + angry situations, we make a SECOND AI call
                // to generate suggested responses the agent can use.
                //
                // This returns a List<SuggestedResponse> with multiple options:
                // - Empathetic acknowledgment
                // - Immediate action promise
                // - Compensation offer (if applicable)
                //
                // The agent can choose, customize, and send these quickly.
                List<SuggestedResponse> responses = ticketAnalysisService
                        .generateUrgentResponses(analysis, provider, model);

                // Return enhanced response with suggested messages
                // The UI can use this to:
                // - Show red alert/banner
                // - Display pre-written responses for quick sending
                // - Trigger escalation notifications
                return Map.of(
                        "success", true,
                        "analysis", analysis,
                        "responses", responses,  // Additional field for urgent cases
                        "priorityColor", ticketAnalysisService.getPriorityColor(analysis.getPriority()),
                        "sentimentEmoji", ticketAnalysisService.getSentimentEmoji(analysis.getSentiment())
                );
            }

            // ==================== STEP 3: RETURN STANDARD ANALYSIS ====================
            //
            // For non-critical tickets, return the analysis with visual helpers:
            // - priorityColor: Hex color for UI display (red, orange, yellow, green)
            // - sentimentEmoji: Quick visual indicator of customer mood
            //
            // The UI can use these for:
            // - Color-coded ticket list
            // - Quick sentiment scanning
            // - Priority-based sorting
            return Map.of(
                    "success", true,
                    "analysis", analysis,
                    "priorityColor", ticketAnalysisService.getPriorityColor(analysis.getPriority()),
                    "sentimentEmoji", ticketAnalysisService.getSentimentEmoji(analysis.getSentiment())
            );

        } catch (Exception e) {
            // ==================== ERROR HANDLING ====================
            //
            // Return structured error response instead of throwing exception.
            // This allows the UI to display a user-friendly error message.
            //
            // Common errors:
            // - AI API timeout
            // - Invalid JSON response from AI
            // - Rate limiting
            return Map.of(
                    "success", false,
                    "error", "Failed to analyze ticket: " + e.getMessage()
            );
        }

    }
}