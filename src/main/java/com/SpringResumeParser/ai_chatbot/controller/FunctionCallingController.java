package com.SpringResumeParser.ai_chatbot.controller;

import com.SpringResumeParser.ai_chatbot.service.FunctionCallingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST Controller for AI Support Bot with Function Calling capabilities.
 *
 * KEY CONCEPT: AI That Takes Action
 *
 * This controller exposes two endpoints that demonstrate the progression
 * of function calling capabilities:
 *
 * 1. /chat/basic - AI with ONE tool (getOrderStatus)
 * 2. /chat/full  - AI with MULTIPLE tools (track, cancel, return, refund)
 *
 * HOW FUNCTION CALLING CHANGES THE GAME:
 *
 * WITHOUT Function Calling:
 *   User: "Where's my order ORD-123?"
 *   AI: "I don't have access to order information. Please contact support."
 *
 * WITH Function Calling:
 *   User: "Where's my order ORD-123?"
 *   AI: [calls getOrderStatus("ORD-123")]
 *   AI: "Your order ORD-123 shipped yesterday! It's currently in transit
 *        and should arrive by Friday. Tracking: 1Z999AA10123456784"
 *
 * The AI doesn't just KNOW about orders - it can LOOK THEM UP in real-time.
 *
 * @author HungryCoders
 */
@RestController
@RequestMapping("/api/support")
public class FunctionCallingController {

    /**
     * Service that handles AI chat with function calling logic.
     * Contains the ChatClient configuration and tool registration.
     */
    @Autowired
    private FunctionCallingService functionCallingService;

    /**
     * Basic support chat with single tool: Order Tracking.
     *
     * ENDPOINT: POST /api/support/chat/basic
     *
     * This endpoint demonstrates the simplest form of function calling.
     * The AI has access to ONE tool: getOrderStatus()
     *
     * WHAT THE AI CAN DO:
     * - Check order status
     * - Get tracking information
     * - Provide delivery estimates
     *
     * WHAT THE AI CANNOT DO (yet):
     * - Cancel orders
     * - Process returns
     * - Check refunds
     *
     * EXAMPLE CONVERSATION:
     *
     * User: "Hi, I placed an order yesterday. Order number is ORD-456.
     *        Can you tell me when it will arrive?"
     *
     * AI Internal Process:
     *   1. Understands user wants order status
     *   2. Identifies orderId = "ORD-456"
     *   3. Decides to call getOrderStatus("ORD-456")
     *   4. Receives: { status: "PROCESSING", estimatedDelivery: "Dec 20" }
     *   5. Formulates natural response
     *
     * AI Response: "I found your order ORD-456! It's currently being processed
     *              and is estimated to arrive by December 20th."
     *
     * QUERY PARAMETERS:
     * - provider: AI provider to use (openai, gemini). Default: openai
     * - model: Specific model to use. Default: gpt-4o
     *
     * REQUEST BODY:
     * {
     *     "message": "Where is my order ORD-456?"
     * }
     *
     * RESPONSE:
     * {
     *     "success": true,
     *     "response": "Your order ORD-456 is currently..."
     * }
     *
     * @param request Map containing "message" key with user's question
     * @param provider AI provider (openai or gemini)
     * @param model Specific model to use
     * @return Response map with success status and AI response
     */
    @PostMapping("/chat/basic")
    public Map<String, Object> basicSupportChat(
            @RequestBody Map<String, String> request,
            @RequestParam(defaultValue = "openai") String provider,
            @RequestParam(defaultValue = "gpt-4o") String model
    ) {
        String userMessage = request.get("message");

        // Input validation - ensure message is provided
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return Map.of(
                    "success", false,
                    "error", "Message cannot be empty"
            );
        }

        try {
            // Call service with basic tool set (order tracking only)
            // The AI will automatically call getOrderStatus() if the user
            // asks about order status, tracking, or delivery
            String response = functionCallingService.chatWithOrderTracking(
                    userMessage, provider, model);

            return Map.of(
                    "success", true,
                    "response", response
            );

        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", "Failed: " + e.getMessage()
            );
        }
    }

    /**
     * Full support chat with multiple tools.
     *
     * ENDPOINT: POST /api/support/chat/full
     *
     * This endpoint demonstrates the power of MULTIPLE function calling.
     * The AI has access to a complete toolkit and chooses which to use.
     *
     * AVAILABLE TOOLS:
     *
     * | Tool | Purpose | Example Trigger |
     * |------|---------|-----------------|
     * | getOrderStatus() | Check order status | "Where's my order?" |
     * | cancelOrder() | Cancel unshipped order | "Cancel my order" |
     * | initiateReturn() | Start return process | "I want to return this" |
     * | checkRefund() | Check refund status | "Did I get my refund?" |
     *
     * AI TOOL SELECTION:
     *
     * The AI reads ALL tool descriptions and decides which one(s) to call
     * based on user intent. It can even call MULTIPLE tools in sequence.
     *
     * MULTI-TOOL CONVERSATION EXAMPLE:
     *
     * User: "I need to cancel order ORD-789 and check if ORD-456 was refunded"
     *
     * AI Process:
     *   1. Identifies TWO intents: cancel + check refund
     *   2. Calls cancelOrder("ORD-789", "user requested")
     *   3. Calls checkRefund("ORD-456")
     *   4. Combines both results into one response
     *
     * AI Response: "I've cancelled order ORD-789 for you. Your refund will
     *              be processed in 3-5 business days. As for ORD-456, your
     *              refund of $49.99 was completed on December 10th."
     *
     * SMART TOOL SELECTION EXAMPLE:
     *
     * User: "I don't want this order anymore" (ambiguous - cancel or return?)
     *
     * AI might ask: "I'd be happy to help! Could you provide your order number?
     *               Also, has the order been delivered yet? If not, I can cancel
     *               it. If it has arrived, I can start a return for you."
     *
     * The AI understands the DIFFERENCE between cancel and return!
     *
     * @param request Map containing "message" key with user's question
     * @param provider AI provider (openai or gemini)
     * @param model Specific model to use
     * @return Response map with success status and AI response
     */
    @PostMapping("/chat/full")
    public Map<String, Object> fullSupportChat(
            @RequestBody Map<String, String> request,
            @RequestParam(defaultValue = "openai") String provider,
            @RequestParam(defaultValue = "gpt-4o") String model
    ) {
        String userMessage = request.get("message");

        // Input validation
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return Map.of(
                    "success", false,
                    "error", "Message cannot be empty"
            );
        }

        try {
            // Call service with FULL tool set
            // AI has access to ALL functions and intelligently chooses
            // which one(s) to call based on user's message
            String response = functionCallingService.chatWithFullSupport(
                    userMessage, provider, model);

            return Map.of(
                    "success", true,
                    "response", response
            );

        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", "Failed: " + e.getMessage()
            );
        }
    }
}