package com.SpringResumeParser.ai_chatbot.model;

/**
 * Model class representing an AI-generated suggested response for support agents.
 *
 * KEY CONCEPT: AI-Generated Response Templates
 *
 * When a ticket is identified as critical/urgent, we ask the AI to generate
 * multiple response options that support agents can use or customize.
 * This speeds up response time for critical situations.
 *
 * This class is the TARGET for converting AI-generated JSON into Java objects.
 * Used with ParameterizedTypeReference to deserialize a list of responses:
 *
 *   .entity(new ParameterizedTypeReference<List<SuggestedResponse>>() {})
 *
 * EXAMPLE AI JSON RESPONSE:
 * [
 *   {
 *     "tone": "empathetic",
 *     "responseText": "I sincerely apologize for the inconvenience...",
 *     "estimatedReadingTime": 15
 *   },
 *   {
 *     "tone": "solution-focused",
 *     "responseText": "I've immediately escalated this to our team...",
 *     "estimatedReadingTime": 20
 *   },
 *   {
 *     "tone": "compensation",
 *     "responseText": "As a gesture of goodwill, we'd like to offer...",
 *     "estimatedReadingTime": 25
 *   }
 * ]
 *
 * WHY MULTIPLE RESPONSES?
 * - Different situations need different tones
 * - Agents can choose the most appropriate one
 * - Reduces time spent drafting responses from scratch
 * - Ensures consistent, professional messaging
 *
 * REQUIREMENTS FOR AI-TO-OBJECT MAPPING:
 * 1. Must have a no-args constructor (for deserialization)
 * 2. Field names must match JSON keys from AI response
 * 3. All fields need getters/setters
 *
 * @author Karthik
 */
public class SuggestedResponse {

    /**
     * The emotional tone/approach of this response.
     *
     * Examples:
     * - "empathetic": Acknowledges customer frustration
     * - "solution-focused": Emphasizes immediate action taken
     * - "compensation": Offers something to make up for the issue
     * - "apologetic": Focuses on sincere apology
     * - "professional": Neutral, business-like tone
     *
     * Helps agents quickly identify which response fits the situation.
     */
    private String tone;

    /**
     * The actual response text that can be sent to the customer.
     *
     * This is a ready-to-use or easily customizable message.
     * Agents can:
     * - Send as-is for speed
     * - Customize with specific details (order numbers, names)
     * - Use as a starting point for a more detailed response
     */
    private String responseText;

    /**
     * Estimated time in seconds to read this response.
     *
     * Useful for:
     * - Helping agents choose shorter responses for quick issues
     * - Setting customer expectations for response length
     * - UI display (e.g., "15 sec read")
     *
     * Typically calculated as: word count / 200 * 60 (avg reading speed)
     */
    private int estimatedReadingTime;

    /**
     * No-args constructor required for JSON deserialization.
     *
     * When Spring AI (or Jackson) converts JSON to this object,
     * it first creates an empty instance using this constructor,
     * then calls setters for each field.
     */
    public SuggestedResponse() {
    }

    /**
     * All-args constructor for programmatic creation.
     * Useful for testing or manual object creation.
     *
     * @param tone The emotional tone of the response
     * @param responseText The actual message text
     * @param estimatedReadingTime Reading time in seconds
     */
    public SuggestedResponse(String tone, String responseText, int estimatedReadingTime) {
        this.tone = tone;
        this.responseText = responseText;
        this.estimatedReadingTime = estimatedReadingTime;
    }

    // ==================== GETTERS AND SETTERS ====================
    // Required for JSON serialization/deserialization

    public String getTone() {
        return tone;
    }

    public void setTone(String tone) {
        this.tone = tone;
    }

    public String getResponseText() {
        return responseText;
    }

    public void setResponseText(String responseText) {
        this.responseText = responseText;
    }

    public int getEstimatedReadingTime() {
        return estimatedReadingTime;
    }

    public void setEstimatedReadingTime(int estimatedReadingTime) {
        this.estimatedReadingTime = estimatedReadingTime;
    }
}