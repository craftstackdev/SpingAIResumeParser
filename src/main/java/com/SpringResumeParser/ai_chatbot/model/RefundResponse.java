package com.SpringResumeParser.ai_chatbot.model;

public record RefundResponse(
        String orderId,
        String refundStatus,
        double refundAmount,
        String estimatedDate
) {}
