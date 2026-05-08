package com.SpringResumeParser.ai_chatbot.config;

import com.SpringResumeParser.ai_chatbot.model.CancelResponse;
import com.SpringResumeParser.ai_chatbot.model.OrderResponse;
import com.SpringResumeParser.ai_chatbot.model.RefundResponse;
import com.SpringResumeParser.ai_chatbot.model.ReturnResponse;
import com.SpringResumeParsers.ai_chatbot.model.*;
import com.SpringResumeParser.ai_chatbot.service.OrderService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Configuration class that defines AI-callable tools/functions.
 *
 * KEY CONCEPT: Function Calling (Tool Use)
 *
 * Function calling allows AI to DO things, not just SAY things.
 * Instead of just generating text, the AI can:
 * - Query databases
 * - Call external APIs
 * - Perform business operations
 * - Access real-time data
 *
 * HOW IT WORKS:
 *
 * 1. You define methods with @Tool annotation
 * 2. Spring AI registers these as "available tools" with the LLM
 * 3. User asks: "What's the status of order ORD-123?"
 * 4. AI recognizes it needs data → decides to call getOrderStatus("ORD-123")
 * 5. Spring AI executes your Java method
 * 6. Result is sent back to AI
 * 7. AI formulates a natural language response using the data
 *
 * THE MAGIC:
 * AI doesn't just pattern-match keywords. It UNDERSTANDS intent.
 * "Where's my order ORD-123?" → calls getOrderStatus()
 * "I want to cancel ORD-123" → calls cancelOrder()
 * "Did I get my refund for ORD-123?" → calls checkRefund()
 *
 * WHY @Service?
 * This class is a Spring bean so it can:
 * - Be autowired into controllers
 * - Have dependencies injected (OrderService)
 * - Be discovered by Spring AI for tool registration
 *
 * @author Karthik
 */
@Service
public class FunctionConfig

    /**
     * Service containing the actual business logic for order operations.
     * Tools are thin wrappers - real logic lives in service classes.
     */
    @Autowired
    private OrderService orderService;

    /**
     * Tool: Get Order Status
     *
     * Allows AI to retrieve current status, tracking info, and delivery
     * estimate for any order.
     *
     * KEY ANNOTATIONS:
     *
     * @Tool - Marks this method as an AI-callable function
     *   - description: Tells the AI WHEN to use this tool
     *   - AI reads this to decide if this tool helps answer the user's question
     *   - Be specific! Good descriptions = better tool selection by AI
     *
     * @ToolParam - Describes each parameter
     *   - description: Tells AI what value to extract from user's message
     *   - AI uses this to map "order ORD-123" → orderId = "ORD-123"
     *
     * EXAMPLE CONVERSATION:
     *
     * User: "Can you check on my order ORD-789?"
     *
     * AI thinks: "User wants order status. I have getOrderStatus tool.
     *            I need to extract orderId = 'ORD-789'"
     *
     * AI calls: getOrderStatus("ORD-789")
     *
     * Tool returns: OrderResponse { status: "SHIPPED", tracking: "1Z999..." }
     *
     * AI responds: "Your order ORD-789 has shipped! Tracking number is 1Z999..."
     *
     * @param orderId The order ID extracted from user's message by AI
     * @return OrderResponse with status, tracking, and delivery info
     */
    @Tool(description = "Get order status, tracking info, and delivery estimate by order ID")
    public OrderResponse getOrderStatus(
            @ToolParam(description = "The order ID to check")
            String orderId
    ) {
        System.out.println("🔧 AI used tool: getOrderStatus(" + orderId + ")");
        return orderService.getOrderStatus(orderId);
    }


    /**
     * Tool: Cancel Order
     *
     * Allows AI to cancel orders that haven't shipped yet.
     * Demonstrates a tool with MULTIPLE parameters.
     *
     * MULTI-PARAMETER EXTRACTION:
     *
     * User: "Please cancel order ORD-456, I found it cheaper elsewhere"
     *
     * AI extracts:
     *   - orderId = "ORD-456"
     *   - reason = "found it cheaper elsewhere"
     *
     * AI calls: cancelOrder("ORD-456", "found it cheaper elsewhere")
     *
     * BUSINESS LOGIC NOTE:
     * The tool description says "hasn't shipped yet" - this sets expectations.
     * If OrderService returns a failure (already shipped), AI will explain
     * this to the user naturally.
     *
     * @param orderId The order ID to cancel
     * @param reason Why the customer wants to cancel (for records)
     * @return CancelResponse with success status and refund info
     */
    @Tool(description = "Cancel an order that hasn't shipped yet. Returns refund info if successful.")
    public CancelResponse cancelOrder(
            @ToolParam(description = "The order ID to cancel")
            String orderId,
            @ToolParam(description = "Reason for cancellation")
            String reason
    ) {
        System.out.println("🔧 AI used tool: cancelOrder(" + orderId + ", reason: " + reason + ")");
        return orderService.cancelOrder(orderId, reason);
    }

    /**
     * Tool: Initiate Return
     *
     * Allows AI to start the return process for delivered orders.
     *
     * TOOL SELECTION BY AI:
     *
     * The AI must choose between similar tools:
     * - cancelOrder: For orders NOT yet delivered
     * - initiateReturn: For orders ALREADY delivered
     *
     * The description helps AI make the right choice:
     * "Start return process for a DELIVERED order"
     *
     * EXAMPLE:
     *
     * User: "I want to return order ORD-111, the size is wrong"
     *
     * AI thinks: "User has received the item (wants to return, not cancel).
     *            I should use initiateReturn, not cancelOrder."
     *
     * AI calls: initiateReturn("ORD-111", "the size is wrong")
     *
     * @param orderId The order ID to return
     * @param reason Why the customer is returning (required for processing)
     * @return ReturnResponse with return label and instructions
     */
    @Tool(description = "Start return process for a delivered order. Customer must provide reason for return.")
    public ReturnResponse initiateReturn(
            @ToolParam(description = "The order ID to return")
            String orderId,
            @ToolParam(description = "Reason for return")
            String reason
    ) {
        System.out.println("🔧 AI used tool: initiateReturn(" + orderId + ", reason: " + reason + ")");
        return orderService.initiateReturn(orderId, reason);
    }

    /**
     * Tool: Check Refund Status
     *
     * Allows AI to check if/when a refund was processed.
     *
     * CONVERSATIONAL CONTEXT:
     *
     * This tool is often called as a FOLLOW-UP after cancelOrder.
     *
     * Conversation flow:
     * 1. User: "Cancel order ORD-222"
     * 2. AI calls cancelOrder() → "Cancelled, refund in 3-5 days"
     * 3. User: "Did the refund go through?"
     * 4. AI calls checkRefund() → Shows refund status
     *
     * AI maintains context and knows which order the user means.
     *
     * @param orderId The order ID to check refund status for
     * @return RefundResponse with refund status, amount, and date
     */
    @Tool(description = "Check the status of a refund for a cancelled order")
    public RefundResponse checkRefund(
            @ToolParam(description = "The order ID to check refund status for")
            String orderId
    ) {
        System.out.println("🔧 AI used tool: checkRefund(" + orderId + ")");
        return orderService.checkRefund(orderId);
    }
}