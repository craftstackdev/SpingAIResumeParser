package com.SpringResumeParser.ai_chatbot.model;

/**
 * IMPORTANT: All records MUST be public for Spring AI tool calling!
 * AI needs to serialize/deserialize these as JSON
 */

// Order Status Tool - Input/Output
public record OrderRequest(String orderId) {}

