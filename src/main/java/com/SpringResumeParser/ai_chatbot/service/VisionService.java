package com.SpringResumeParser.ai_chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Service
public class VisionService {

    private static final Logger log = LoggerFactory.getLogger(VisionService.class);

    private final ChatClient chatClient;

    public VisionService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Analyze resume image and provide a brief summary
     * Uses GPT-4 Vision for quick analysis
     */
    public String analyzeResume(MultipartFile file) {
        try {
            log.info("Analyzing resume with GPT-4 Vision: {}", file.getOriginalFilename());

            // Convert file to Resource
            Resource imageResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            // Create user message with image
            String textPrompt = """
                    Analyze this resume image and provide a brief summary (max 100 words).
                    Include:
                    - Candidate name
                    - Current role
                    - Years of experience
                    - Key skills (top 5)
                    - Education level
                    """;

            // Call ChatClient with media
            String response = chatClient.prompt()
                    .user(u -> u.text(textPrompt)
                            .media(MimeTypeUtils.IMAGE_PNG, imageResource))
                    .call()
                    .content();

            log.info("Resume analysis completed");
            return response;

        } catch (Exception e) {
            log.error("Error analyzing resume with Vision API", e);
            throw new RuntimeException("Failed to analyze resume: " + e.getMessage(), e);
        }
    }
}