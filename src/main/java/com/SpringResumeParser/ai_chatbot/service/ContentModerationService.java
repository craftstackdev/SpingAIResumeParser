package com.SpringResumeParser.ai_chatbot.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * AI-powered Content Moderation Service
 *
 * Uses GPT-4 Vision to detect inappropriate content:
 * - Nudity / sexually explicit content
 * - Violence / gore
 * - Hate symbols
 * - Offensive gestures
 * - Weapons
 * - Drugs / alcohol abuse
 *
 * This is a REQUIRED security layer for user-generated content!
 *
 * @author HungryCoders
 */
@Service
public class ContentModerationService {

    @Autowired
    private OpenAiChatModel chatModel;

    /**
     * Moderate image content using AI vision.
     *
     * CRITICAL: Always call this AFTER ImageValidationService but BEFORE processing!
     *
     * @param file The image file to moderate
     * @return ModerationResult with safe/unsafe flag and reason
     * @throws IOException if file cannot be read
     */
    public ModerationResult moderateImage(MultipartFile file) throws IOException {

        // Convert file to Resource
        Resource imageResource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };

        // Create vision message with moderation prompt
        String moderationPrompt = """
            You are a content moderation AI. Analyze this image and determine if it contains 
            ANY of the following inappropriate content:
            
            REJECT if image contains:
            1. Nudity or sexually explicit content (exposed private parts, sexual acts)
            2. Partial nudity or suggestive poses (underwear, bikini in non-beach context)
            3. Violence or gore (blood, injuries, weapons being used)
            4. Hate symbols (swastikas, KKK imagery, racist symbols)
            5. Offensive gestures (middle finger, gang signs)
            6. Illegal drugs or drug paraphernalia
            7. Graphic alcohol abuse
            8. Dead bodies or severe injuries
            9. Self-harm imagery
            10. Child exploitation of any kind
            
            ACCEPT if image contains:
            - Professional headshots
            - Business attire photos
            - Resume screenshots
            - Identification documents
            - Certificates / diplomas
            - Work samples (design, art - if appropriate)
            
            Respond ONLY in this JSON format:
            {
              "safe": true/false,
              "category": "SAFE" or specific violation category,
              "confidence": 0.0-1.0,
              "reason": "brief explanation"
            }
            
            Be strict - when in doubt, mark as unsafe. Err on the side of caution.
            """;

        ChatClient chatClient = ChatClient.create(chatModel);

        try {

            String response = chatClient
                    .prompt()
                    .user(u -> u.text(moderationPrompt)
                            .media(MimeTypeUtils.IMAGE_PNG, imageResource))
                    .call()
                    .content();

            // Parse JSON response
            return parseModeration(response);

        } catch (Exception e) {
            // If moderation fails, reject to be safe
            return new ModerationResult(
                    false,
                    "MODERATION_ERROR",
                    0.0,
                    "Content moderation failed: " + e.getMessage()
            );
        }
    }

    /**
     * Parse AI response to ModerationResult
     */
    private ModerationResult parseModeration(String jsonResponse) {
        try {
            // Remove markdown code blocks if present
            String cleanJson = jsonResponse
                    .replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*", "")
                    .trim();

            // Simple JSON parsing (in production, use Jackson or Gson)
            boolean safe = cleanJson.contains("\"safe\": true") ||
                    cleanJson.contains("\"safe\":true");

            String category = extractValue(cleanJson, "category");
            double confidence = extractConfidence(cleanJson);
            String reason = extractValue(cleanJson, "reason");

            return new ModerationResult(safe, category, confidence, reason);

        } catch (Exception e) {
            // Parse error = reject to be safe
            return new ModerationResult(
                    false,
                    "PARSE_ERROR",
                    0.0,
                    "Failed to parse moderation result"
            );
        }
    }

    private String extractValue(String json, String key) {
        try {
            int start = json.indexOf("\"" + key + "\"");
            if (start == -1) return "UNKNOWN";

            start = json.indexOf(":", start) + 1;
            start = json.indexOf("\"", start) + 1;
            int end = json.indexOf("\"", start);

            return json.substring(start, end);
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private double extractConfidence(String json) {
        try {
            int start = json.indexOf("\"confidence\"");
            if (start == -1) return 0.5;

            start = json.indexOf(":", start) + 1;
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);

            String value = json.substring(start, end).trim();
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0.5;
        }
    }

    /**
     * Moderation result data class
     */
    public static class ModerationResult {
        private final boolean safe;
        private final String category;
        private final double confidence;
        private final String reason;

        public ModerationResult(boolean safe, String category, double confidence, String reason) {
            this.safe = safe;
            this.category = category;
            this.confidence = confidence;
            this.reason = reason;
        }

        public boolean isSafe() {
            return safe;
        }

        public String getCategory() {
            return category;
        }

        public double getConfidence() {
            return confidence;
        }

        public String getReason() {
            return reason;
        }

        @Override
        public String toString() {
            return String.format("ModerationResult{safe=%s, category='%s', confidence=%.2f, reason='%s'}",
                    safe, category, confidence, reason);
        }
    }

    /**
     * Custom exception for content moderation failures
     */
    public static class ContentModerationException extends Exception {
        private final String category;
        private final String reason;

        public ContentModerationException(String category, String reason) {
            super("Content moderation failed: " + reason);
            this.category = category;
            this.reason = reason;
        }

        public String getCategory() {
            return category;
        }

        public String getReason() {
            return reason;
        }
    }
}