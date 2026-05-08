package com.SpringResumeParser.ai_chatbot.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.Ordered;

import java.util.regex.Pattern;

/**
 * Advisor that automatically redacts Personally Identifiable Information (PII)
 * from user messages BEFORE they are sent to the AI model.
 *
 * KEY CONCEPT: What are Advisors in Spring AI?
 *
 * Advisors are like middleware/interceptors for AI requests and responses.
 * They allow you to:
 * - Modify requests BEFORE they go to the AI
 * - Modify responses AFTER they come back from the AI
 * - Add cross-cutting concerns (logging, security, caching, etc.)
 * - Build reusable processing pipelines
 *
 * ADVISOR TYPES IN SPRING AI:
 *
 * | Type | Interface | When It Runs |
 * |------|-----------|--------------|
 * | Call Advisor | CallAdvisor | Around blocking .call() requests |
 * | Stream Advisor | StreamAdvisor | Around streaming .stream() requests |
 *
 * THE CHAIN PATTERN:
 *
 * Multiple advisors execute in a chain (like servlet filters):
 *
 *   User Request
 *        ↓
 *   [Advisor 1] ──→ Pre-process
 *        ↓
 *   [Advisor 2] ──→ Pre-process
 *        ↓
 *   [AI Model Call]
 *        ↓
 *   [Advisor 2] ←── Post-process
 *        ↓
 *   [Advisor 1] ←── Post-process
 *        ↓
 *   User Response
 *
 * WHY PII REDACTION MATTERS:
 *
 * When users chat with AI, they might accidentally share sensitive data:
 * - "My email is john@example.com and my card is 4111-1111-1111-1111"
 * - "Call me at 555-123-4567, my SSN is 123-45-6789"
 *
 * This data gets sent to external AI providers (OpenAI, etc.)
 * For privacy/compliance (GDPR, HIPAA, PCI-DSS), we should redact it.
 *
 * WHAT THIS ADVISOR DOES:
 *
 * Before: "Email me at john@example.com about order ORD-123"
 * After:  "Email me at [EMAIL_REDACTED] about order ORD-123"
 *
 * The AI never sees the actual email - only the redacted placeholder.
 *
 * @author Karthik
 */
public class PiiRedactionAdvisor implements CallAdvisor {

    // ==================== PII DETECTION PATTERNS ====================
    //
    // Regular expressions to identify sensitive data in text.
    // These patterns are compiled once and reused for efficiency.

    /**
     * Pattern to detect email addresses.
     *
     * Matches: john@example.com, user.name+tag@domain.co.uk
     *
     * Breakdown:
     * - [A-Za-z0-9._%+-]+ : Local part (before @)
     * - @                  : Literal @ symbol
     * - [A-Za-z0-9.-]+     : Domain name
     * - \.[A-Z|a-z]{2,}    : TLD (.com, .org, .co.uk)
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"
    );

    /**
     * Pattern to detect US phone numbers in various formats.
     *
     * Matches:
     * - 555-123-4567
     * - (555) 123-4567
     * - 555.123.4567
     * - +1-555-123-4567
     * - 5551234567
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\b(?:\\+?1[-.]?)?\\(?([0-9]{3})\\)?[-.]?([0-9]{3})[-.]?([0-9]{4})\\b"
    );

    /**
     * Pattern to detect credit card numbers.
     *
     * Matches 16-digit numbers with optional spaces or dashes:
     * - 4111111111111111
     * - 4111-1111-1111-1111
     * - 4111 1111 1111 1111
     *
     * Note: This is a basic pattern. Production systems should use
     * more sophisticated validation (Luhn algorithm, BIN ranges).
     */
    private static final Pattern CARD_PATTERN = Pattern.compile(
            "\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"
    );

    /**
     * Pattern to detect US Social Security Numbers.
     *
     * Matches: 123-45-6789 (standard SSN format)
     */
    private static final Pattern SSN_PATTERN = Pattern.compile(
            "\\b\\d{3}-\\d{2}-\\d{4}\\b"
    );

    // ==================== ADVISOR INTERFACE METHODS ====================

    /**
     * Returns the name of this advisor for logging and debugging.
     *
     * This name appears in logs when tracing advisor execution.
     * Using getSimpleName() returns "PiiRedactionAdvisor".
     *
     * @return The simple class name of this advisor
     */
    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * Determines the execution order of this advisor in the chain.
     *
     * KEY CONCEPT: Advisor Ordering
     *
     * When multiple advisors are registered, order matters:
     * - HIGHEST_PRECEDENCE (Integer.MIN_VALUE): Executes FIRST
     * - LOWEST_PRECEDENCE (Integer.MAX_VALUE): Executes LAST
     *
     * For PII redaction, we use HIGHEST_PRECEDENCE because:
     * - We want to redact BEFORE any other processing
     * - We want to redact BEFORE logging advisors see the data
     * - Security should always come first
     *
     * EXAMPLE ORDER:
     *
     * | Order Value | Advisor | Purpose |
     * |-------------|---------|---------|
     * | HIGHEST (first) | PiiRedactionAdvisor | Redact sensitive data |
     * | 0 | LoggingAdvisor | Log sanitized requests |
     * | 100 | CachingAdvisor | Check/store cache |
     * | LOWEST (last) | MetricsAdvisor | Record timing |
     *
     * @return Ordered.HIGHEST_PRECEDENCE to ensure this runs first
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;  // Execute FIRST in chain
    }

    /**
     * The main advisor method - intercepts and processes the AI call.
     *
     * KEY CONCEPT: The adviseCall Method
     *
     * This method is called for every ChatClient.call() invocation.
     * It receives:
     * - request: The original ChatClientRequest (contains prompt, options, etc.)
     * - chain: The advisor chain to continue processing
     *
     * THE PATTERN:
     *
     * 1. Extract data from request
     * 2. Process/modify as needed (pre-processing)
     * 3. Call chain.nextCall() to continue to next advisor or AI
     * 4. Optionally process response (post-processing)
     * 5. Return response
     *
     * CRITICAL: Always call chain.nextCall()!
     * If you don't, the request never reaches the AI model.
     *
     * REQUEST MUTATION:
     *
     * ChatClientRequest is immutable, so we use the builder pattern:
     *
     *   request.mutate()           // Start building a copy
     *       .prompt(newPrompt)     // Change the prompt
     *       .build();              // Create new immutable request
     *
     * This creates a NEW request with modified content while
     * preserving all other settings (options, tools, etc.).
     *
     * @param request The incoming chat request
     * @param chain The advisor chain for continuing processing
     * @return The response from the AI (possibly modified)
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {

        // ==================== STEP 1: EXTRACT ORIGINAL MESSAGE ====================
        //
        // Get the user's message text from the prompt.
        // This is what the user typed that might contain PII.
        String originalMessage = request.prompt().getUserMessage().getText();

        // ==================== STEP 2: REDACT PII ====================
        //
        // Apply all regex patterns to find and replace sensitive data.
        // Each pattern replaces matches with a placeholder like [EMAIL_REDACTED].
        String redactedMessage = redactPii(originalMessage);

        // ==================== STEP 3: LOG REDACTION (FOR DEBUGGING) ====================
        //
        // Only log if we actually redacted something.
        // In production, you might want to:
        // - Use proper logging framework (SLF4J)
        // - Log to security audit trail
        // - NOT log the original message (it contains PII!)
        if (!originalMessage.equals(redactedMessage)) {
            System.out.println("[PII REDACTION] Sensitive data redacted from user message");
            System.out.println("Original: " + originalMessage);
            System.out.println("Redacted: " + redactedMessage);
        }

        // ==================== STEP 4: CREATE MODIFIED REQUEST ====================
        //
        // Build a new request with the redacted message.
        //
        // request.mutate() - Creates a builder from the existing request
        // .prompt(...) - Sets the new prompt with redacted message
        // augmentUserMessage() - Replaces user message content while keeping structure
        // .build() - Creates the new immutable request
        //
        // All other request properties (system prompt, tools, options) are preserved.
        ChatClientRequest modifiedRequest = request.mutate()
                .prompt(request.prompt().augmentUserMessage(redactedMessage))
                .build();

        // ==================== STEP 5: CONTINUE THE CHAIN ====================
        //
        // Pass the MODIFIED request to the next advisor (or to the AI model
        // if this is the last advisor in the chain).
        //
        // IMPORTANT: The AI model will only see the redacted message!
        // The original PII never leaves your server.
        return chain.nextCall(modifiedRequest);
    }

    /**
     * Applies all PII detection patterns to redact sensitive information.
     *
     * Each pattern is applied in sequence, replacing matches with
     * descriptive placeholders.
     *
     * REDACTION PLACEHOLDERS:
     *
     * | Data Type | Placeholder |
     * |-----------|-------------|
     * | Email | [EMAIL_REDACTED] |
     * | Phone | [PHONE_REDACTED] |
     * | Credit Card | [CARD_REDACTED] |
     * | SSN | [SSN_REDACTED] |
     *
     * EXAMPLE:
     *
     * Input:  "Contact john@email.com at 555-123-4567"
     * Output: "Contact [EMAIL_REDACTED] at [PHONE_REDACTED]"
     *
     * @param text The original text that may contain PII
     * @return Text with all detected PII replaced with placeholders
     */
    private String redactPii(String text) {
        // Handle null or empty input gracefully
        if (text == null || text.trim().isEmpty()) {
            return text;
        }

        // Apply each pattern sequentially
        // Order doesn't matter since patterns don't overlap
        String redacted = text;
        redacted = EMAIL_PATTERN.matcher(redacted).replaceAll("[EMAIL_REDACTED]");
        redacted = PHONE_PATTERN.matcher(redacted).replaceAll("[PHONE_REDACTED]");
        redacted = CARD_PATTERN.matcher(redacted).replaceAll("[CARD_REDACTED]");
        redacted = SSN_PATTERN.matcher(redacted).replaceAll("[SSN_REDACTED]");

        return redacted;
    }
}