package com.SpringResumeParser.ai_chatbot.service;

import com.SpringResumeParser.ai_chatbot.config.FunctionConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service that enables AI function calling capabilities.
 *
 * KEY CONCEPT: The .tools() Method
 *
 * This is where the magic happens. The .tools() method tells the AI:
 * "Here are functions you CAN call if you need to."
 *
 * BEFORE .tools():
 *   chatClient.prompt().user(message).call()
 *   → AI can only generate text responses
 *
 * AFTER .tools():
 *   chatClient.prompt().user(message).tools(toolsBean).call()
 *   → AI can ALSO call your Java methods!
 *
 * HOW SPRING AI REGISTERS TOOLS:
 *
 * 1. You pass a bean with @Tool-annotated methods to .tools()
 * 2. Spring AI scans the bean for @Tool methods
 * 3. For each @Tool method, it creates a "tool definition":
 *    - Name: method name (e.g., "getOrderStatus")
 *    - Description: from @Tool annotation
 *    - Parameters: from @ToolParam annotations
 * 4. These definitions are sent to the LLM with your prompt
 * 5. LLM decides if/which tool to call based on user's question
 *
 * WHAT HAPPENS DURING A TOOL CALL:
 *
 *   User: "Where's order ORD-123?"
 *          ↓
 *   ChatClient sends prompt + tool definitions to LLM
 *          ↓
 *   LLM responds: "I want to call getOrderStatus with orderId=ORD-123"
 *          ↓
 *   Spring AI intercepts this, calls YOUR Java method
 *          ↓
 *   Your method returns OrderResponse object
 *          ↓
 *   Spring AI sends result back to LLM
 *          ↓
 *   LLM generates natural language response using the data
 *          ↓
 *   User sees: "Your order ORD-123 is shipped and arriving Friday!"
 *
 * @author Karthik
 */
@Service
public class FunctionCallingService {

    /**
     * Service for resolving ChatClient based on provider.
     */
    @Autowired
    private ModelService modelService;

    /**
     * The bean containing all @Tool-annotated methods.
     *
     * KEY INSIGHT: We inject the ENTIRE service bean, not individual methods.
     * Spring AI will scan this bean and find all @Tool methods automatically.
     *
     * This is the "toolbox" we give to the AI.
     */
    @Autowired
    private FunctionConfig orderSupportTools;

    /**
     * Chat with basic order tracking capability.
     *
     * AI has access to ONE tool: getOrderStatus()
     *
     * THE SIMPLEST FUNCTION CALLING PATTERN:
     *
     * ```java
     * chatClient
     *     .prompt()
     *     .user(message)
     *     .tools(beanWithToolMethods)  // ← This enables function calling
     *     .call()
     *     .content();
     * ```
     *
     * WHAT .tools(orderSupportTools) DOES:
     *
     * 1. Scans FunctionConfig for @Tool methods
     * 2. Finds: getOrderStatus(), cancelOrder(), initiateReturn(), checkRefund()
     * 3. Creates tool definitions for each
     * 4. Sends definitions to LLM with the prompt
     *
     * BUT WAIT - why does basic only use getOrderStatus?
     *
     * Actually, ALL tools are technically available. But without a system
     * prompt guiding the AI, it will use whatever tool matches the user's
     * intent. For a simple demo, users typically only ask about order status.
     *
     * In chatWithFullSupport(), we add a system prompt that explicitly
     * tells the AI about all available tools and how to use them.
     *
     * EXAMPLE FLOW:
     *
     * Input: "Track my order ORD-555"
     *
     * Step 1: ChatClient sends to LLM:
     *         - User message: "Track my order ORD-555"
     *         - Available tools: [getOrderStatus, cancelOrder, ...]
     *
     * Step 2: LLM responds:
     *         "Call getOrderStatus with orderId='ORD-555'"
     *
     * Step 3: Spring AI executes:
     *         orderSupportTools.getOrderStatus("ORD-555")
     *
     * Step 4: Method returns:
     *         OrderResponse { status: "SHIPPED", tracking: "1Z999..." }
     *
     * Step 5: Result sent back to LLM
     *
     * Step 6: LLM generates final response:
     *         "Your order ORD-555 has shipped! Tracking: 1Z999..."
     *
     * @param userMessage The user's question or request
     * @param provider AI provider (openai, gemini)
     * @param model Specific model to use
     * @return AI-generated response (may include data from tool calls)
     */
    public String chatWithOrderTracking(
            String userMessage,
            String provider,
            String model) {

        ChatClient chatClient = modelService.getChatClient(provider);

        return chatClient
                .prompt()
                .options(OpenAiChatOptions.builder()
                        .model(model)
                        .build())
                .user(userMessage)
                .tools(orderSupportTools)  // Enable function calling with this bean
                .call()
                .content();

    }

    /**
     * Chat with full support capabilities and system instructions.
     *
     * AI has access to ALL tools with explicit guidance on how to use them.
     *
     * KEY DIFFERENCE FROM BASIC:
     *
     * This method adds a SYSTEM PROMPT that:
     * 1. Defines the AI's role (customer support assistant)
     * 2. Lists all available tools and their purposes
     * 3. Provides behavioral guidelines (be friendly, confirm actions)
     * 4. Instructs error handling (explain errors clearly)
     *
     * WHY SYSTEM PROMPTS MATTER FOR TOOLS:
     *
     * Without guidance, AI might:
     * - Call tools unnecessarily
     * - Not explain what it's doing
     * - Execute destructive actions without confirmation
     *
     * With system prompt, AI will:
     * - Use tools appropriately
     * - Confirm before cancelling orders
     * - Explain errors in user-friendly language
     *
     * SYSTEM PROMPT BREAKDOWN:
     *
     * "You are a helpful customer support assistant..."
     *   → Sets the persona and tone
     *
     * "You have access to these tools: ..."
     *   → Reinforces tool awareness (LLM already knows from definitions,
     *     but this helps with consistent behavior)
     *
     * "Always confirm actions before executing them."
     *   → CRITICAL for destructive operations like cancelOrder!
     *     AI will ask "Are you sure?" before cancelling
     *
     * "If a tool returns an error, explain it clearly..."
     *   → AI will say "I couldn't cancel because it already shipped"
     *     instead of showing raw error messages
     *
     * MULTI-TOOL EXAMPLE:
     *
     * User: "Cancel ORD-111 and check my refund for ORD-222"
     *
     * AI Process:
     *   1. Identifies 2 intents: cancel + check refund
     *   2. Calls cancelOrder("ORD-111", "user requested")
     *   3. Calls checkRefund("ORD-222")
     *   4. Combines results into coherent response
     *
     * AI Response:
     *   "I've cancelled order ORD-111. Your refund of $29.99 will be
     *    processed in 3-5 days. For ORD-222, your refund of $49.99
     *    was completed on December 10th."
     *
     * @param userMessage The user's question or request
     * @param provider AI provider (openai, gemini)
     * @param model Specific model to use
     * @return AI-generated response with full support capabilities
     */
    public String chatWithFullSupport(
            String userMessage,
            String provider,
            String model) {

        ChatClient chatClient = modelService.getChatClient(provider);

        return chatClient
                .prompt()
                .options(OpenAiChatOptions.builder()
                        .model(model)
                        .build())
                .system("""
                You are a helpful customer support assistant for an e-commerce company.
                
                You have access to these tools:
                - getOrderStatus: Check order status and tracking
                - cancelOrder: Cancel orders that haven't shipped yet
                - initiateReturn: Start return process for delivered orders
                - checkRefund: Check refund status for cancelled orders
                
                Use these tools to help customers. Be friendly and helpful.
                Always confirm actions before executing them.
                If a tool returns an error, explain it clearly to the customer.
                """)
                .user(userMessage)
                .tools(orderSupportTools)  // Same bean - all 4 tools available
                .call()
                .content();

    }
}