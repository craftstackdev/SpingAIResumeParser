package com.SpringResumeParser.ai_chatbot.service;

import com.SpringResumeParser.ai_chatbot.model.CancelResponse;
import com.SpringResumeParser.ai_chatbot.model.OrderResponse;
import com.SpringResumeParser.ai_chatbot.model.RefundResponse;
import com.SpringResumeParser.ai_chatbot.model.ReturnResponse;
import com.SpringResumeParsers.ai_chatbot.model.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Mock order service to simulate database operations
 * In real application, this would connect to actual database
 */
@Service
public class OrderService {

    // Mock database of orders
    private final Map<String, MockOrder> orders = new HashMap<>();

    public OrderService() {
        // Initialize with sample orders
        orders.put("12345", new MockOrder(
                "12345", "OUT_FOR_DELIVERY", "Today by 6 PM",
                "TRK789XYZ", "John Doe", 2499.00
        ));

        orders.put("67890", new MockOrder(
                "67890", "DELIVERED", "Delivered yesterday",
                "TRK123ABC", "Jane Smith", 1299.00
        ));

        orders.put("11111", new MockOrder(
                "11111", "PROCESSING", "Will ship in 2 days",
                "Not yet assigned", "Alice Johnson", 3999.00
        ));

        orders.put("22222", new MockOrder(
                "22222", "SHIPPED", "Arriving tomorrow",
                "TRK456DEF", "Bob Wilson", 5499.00
        ));

        orders.put("33333", new MockOrder(
                "33333", "CANCELLED", "Cancelled by customer",
                "N/A", "Charlie Brown", 999.00
        ));
    }

    /**
     * Get order status by order ID
     */
    public OrderResponse getOrderStatus(String orderId) {
        MockOrder order = orders.get(orderId);

        if (order == null) {
            return new OrderResponse(
                    orderId,
                    "NOT_FOUND",
                    "Order not found",
                    "N/A",
                    "Unknown",
                    0.0
            );
        }

        return new OrderResponse(
                order.orderId,
                order.status,
                order.estimatedDelivery,
                order.trackingNumber,
                order.customerName,
                order.totalAmount
        );
    }

    /**
     * Cancel an order if not yet shipped
     */
    public CancelResponse cancelOrder(String orderId, String reason) {
        MockOrder order = orders.get(orderId);

        if (order == null) {
            return new CancelResponse(
                    false,
                    "Order not found",
                    orderId,
                    0.0
            );
        }

        // Only allow cancellation for PROCESSING orders
        if (order.status.equals("PROCESSING")) {
            order.status = "CANCELLED";
            return new CancelResponse(
                    true,
                    "Order cancelled successfully. Refund will be processed in 3-5 business days.",
                    orderId,
                    order.totalAmount
            );
        } else if (order.status.equals("SHIPPED") || order.status.equals("OUT_FOR_DELIVERY")) {
            return new CancelResponse(
                    false,
                    "Order already shipped. Please initiate a return instead.",
                    orderId,
                    0.0
            );
        } else if (order.status.equals("DELIVERED")) {
            return new CancelResponse(
                    false,
                    "Order already delivered. Please initiate a return instead.",
                    orderId,
                    0.0
            );
        } else {
            return new CancelResponse(
                    false,
                    "Order already cancelled",
                    orderId,
                    0.0
            );
        }
    }

    /**
     * Initiate return for delivered order
     */
    public ReturnResponse initiateReturn(String orderId, String reason) {
        MockOrder order = orders.get(orderId);

        if (order == null) {
            return new ReturnResponse(
                    false,
                    "Order not found",
                    null,
                    null
            );
        }

        // Only allow returns for DELIVERED orders
        if (order.status.equals("DELIVERED")) {
            String returnId = "RET-" + orderId;
            String returnLabel = "LABEL-" + System.currentTimeMillis();

            return new ReturnResponse(
                    true,
                    "Return initiated successfully. Return label sent to your email.",
                    returnId,
                    returnLabel
            );
        } else {
            return new ReturnResponse(
                    false,
                    "Can only return delivered orders. Current status: " + order.status,
                    null,
                    null
            );
        }
    }

    /**
     * Check refund status
     */
    public RefundResponse checkRefund(String orderId) {
        MockOrder order = orders.get(orderId);

        if (order == null) {
            return new RefundResponse(
                    orderId,
                    "NOT_FOUND",
                    0.0,
                    "N/A"
            );
        }

        if (order.status.equals("CANCELLED")) {
            LocalDate refundDate = LocalDate.now().plusDays(3);
            return new RefundResponse(
                    orderId,
                    "PROCESSING",
                    order.totalAmount,
                    refundDate.format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
            );
        } else {
            return new RefundResponse(
                    orderId,
                    "NO_REFUND",
                    0.0,
                    "N/A - Order not cancelled"
            );
        }
    }

    // Inner class for mock orders
    private static class MockOrder {
        String orderId;
        String status;
        String estimatedDelivery;
        String trackingNumber;
        String customerName;
        double totalAmount;

        MockOrder(String orderId, String status, String estimatedDelivery,
                  String trackingNumber, String customerName, double totalAmount) {
            this.orderId = orderId;
            this.status = status;
            this.estimatedDelivery = estimatedDelivery;
            this.trackingNumber = trackingNumber;
            this.customerName = customerName;
            this.totalAmount = totalAmount;
        }
    }
}