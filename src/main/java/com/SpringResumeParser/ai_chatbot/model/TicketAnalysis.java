package com.SpringResumeParser.ai_chatbot.model;

/**
 * Model class representing the structured analysis of a support ticket.
 *
 * KEY CONCEPT: AI Response to Java Object Mapping
 *
 * This class is the TARGET for converting AI-generated JSON into a Java object.
 * When we ask the AI to analyze a ticket, we instruct it to respond in JSON
 * format that matches this class structure. Spring AI then deserializes
 * the JSON into this object automatically.
 *
 * EXAMPLE AI JSON RESPONSE:
 * {
 *     "category": "Technical Support",
 *     "subcategory": "Login Issues",
 *     "priority": "HIGH",
 *     "sentiment": "FRUSTRATED",
 *     "suggestedTeam": "Authentication Team",
 *     "estimatedResolutionMinutes": 30,
 *     "summary": "User unable to login after password reset",
 *     "keyIssues": "Password reset email not received, account locked"
 * }
 *
 * This JSON automatically converts to a TicketAnalysis object that can be
 * used in Java business logic (if/else, routing, database storage, etc.)
 *
 * WHY STRUCTURED OUTPUT MATTERS:
 *
 * Plain Text Response:
 *   "This is a high priority technical issue about login problems..."
 *   → Hard to parse, unreliable, can't use in code logic
 *
 * Structured Response (this class):
 *   TicketAnalysis { priority: HIGH, category: "Technical Support", ... }
 *   → Easy to use: if (analysis.getPriority() == Priority.HIGH) { ... }
 *
 * REQUIREMENTS FOR AI-TO-OBJECT MAPPING:
 * 1. Must have a no-args constructor (for deserialization)
 * 2. Field names must match JSON keys (or use @JsonProperty)
 * 3. Enums must match exact string values from AI response
 * 4. All fields need getters/setters (or use public fields)
 *
 * @author Karthik
 */
public class TicketAnalysis {

    // ==================== TICKET CLASSIFICATION FIELDS ====================

    /**
     * Primary category of the ticket.
     * Examples: "Technical Support", "Billing", "Sales", "Account Management"
     *
     * Used for routing to the correct department.
     */
    private String category;

    /**
     * More specific classification within the category.
     * Examples: "Login Issues", "Payment Failed", "Refund Request"
     *
     * Helps agents understand the specific issue before reading the full ticket.
     */
    private String subcategory;

    /**
     * Urgency level of the ticket.
     * Uses the Priority enum for type-safe comparison in business logic.
     *
     * Enables code like:
     *   if (analysis.getPriority() == Priority.CRITICAL) { escalate(); }
     */
    private Priority priority;

    /**
     * Emotional state of the customer based on ticket text.
     * Uses the Sentiment enum for type-safe comparison.
     *
     * Critical for identifying customers at risk of churn.
     * ANGRY + CRITICAL = immediate escalation needed.
     */
    private Sentiment sentiment;

    // ==================== ROUTING AND RESOLUTION FIELDS ====================

    /**
     * Recommended team or department to handle this ticket.
     * Examples: "Authentication Team", "Billing Support", "Tier 2 Technical"
     *
     * Can be used for automatic ticket assignment in ticketing systems.
     */
    private String suggestedTeam;

    /**
     * AI-estimated time to resolve this issue in minutes.
     * Based on the complexity and type of issue.
     *
     * Useful for:
     * - Setting customer expectations
     * - SLA tracking
     * - Workload distribution
     */
    private int estimatedResolutionMinutes;

    // ==================== CONTENT ANALYSIS FIELDS ====================

    /**
     * Brief one-line summary of the ticket.
     * AI-generated condensed version of the customer's issue.
     *
     * Allows agents to quickly scan tickets without reading full content.
     */
    private String summary;

    /**
     * Comma-separated list of main problems identified in the ticket.
     * Examples: "Password reset failed, Email not received, Account locked"
     *
     * Helps agents prepare solutions before responding.
     */
    private String keyIssues;

    // ==================== CONSTRUCTORS ====================

    /**
     * No-args constructor required for JSON deserialization.
     *
     * When Spring AI (or Jackson) converts JSON to this object,
     * it first creates an empty instance using this constructor,
     * then calls setters for each field.
     */
    public TicketAnalysis() {
    }

    /**
     * All-args constructor for programmatic creation.
     * Useful for testing or manual object creation.
     */
    public TicketAnalysis(String category, String subcategory, Priority priority,
                          Sentiment sentiment, String suggestedTeam,
                          int estimatedResolutionMinutes, String summary, String keyIssues) {
        this.category = category;
        this.subcategory = subcategory;
        this.priority = priority;
        this.sentiment = sentiment;
        this.suggestedTeam = suggestedTeam;
        this.estimatedResolutionMinutes = estimatedResolutionMinutes;
        this.summary = summary;
        this.keyIssues = keyIssues;
    }

    // ==================== GETTERS AND SETTERS ====================
    // Required for JSON serialization/deserialization

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSubcategory() {
        return subcategory;
    }

    public void setSubcategory(String subcategory) {
        this.subcategory = subcategory;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public Sentiment getSentiment() {
        return sentiment;
    }

    public void setSentiment(Sentiment sentiment) {
        this.sentiment = sentiment;
    }

    public String getSuggestedTeam() {
        return suggestedTeam;
    }

    public void setSuggestedTeam(String suggestedTeam) {
        this.suggestedTeam = suggestedTeam;
    }

    public int getEstimatedResolutionMinutes() {
        return estimatedResolutionMinutes;
    }

    public void setEstimatedResolutionMinutes(int estimatedResolutionMinutes) {
        this.estimatedResolutionMinutes = estimatedResolutionMinutes;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getKeyIssues() {
        return keyIssues;
    }

    public void setKeyIssues(String keyIssues) {
        this.keyIssues = keyIssues;
    }

    // ==================== ENUMS ====================

    /**
     * Priority levels for ticket urgency.
     *
     * These values must EXACTLY match what the AI returns in JSON.
     * The prompt template should instruct the AI to use these exact values.
     *
     * Levels:
     * - LOW: General inquiries, no immediate action needed
     * - MEDIUM: Standard issues, respond within SLA
     * - HIGH: Important issues, prioritize handling
     * - CRITICAL: System down, major blocker, immediate escalation
     */
    public enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    /**
     * Customer sentiment/emotional state detected from ticket text.
     *
     * Used for escalation logic and response tone adjustment.
     *
     * Levels:
     * - HAPPY: Positive feedback, compliments, satisfied customer
     * - NEUTRAL: Standard inquiry, no strong emotion
     * - FRUSTRATED: Mild annoyance, repeated issues, impatience
     * - ANGRY: Strong negative emotion, threats to leave, demands
     */
    public enum Sentiment {
        HAPPY, NEUTRAL, FRUSTRATED, ANGRY
    }
}