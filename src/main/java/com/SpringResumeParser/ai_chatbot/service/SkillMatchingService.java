package com.SpringResumeParser.ai_chatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.SpringResumeParser.ai_chatbot.dto.SkillMatchResult;
import com.SpringResumeParser.ai_chatbot.dto.CareerSuggestions;
import com.SpringResumeParser.ai_chatbot.entity.ResumeData;
import com.SpringResumeParser.ai_chatbot.repository.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SkillMatchingService {

    private static final Logger log = LoggerFactory.getLogger(SkillMatchingService.class);

    private final ChatClient chatClient;
    private final ResumeRepository resumeRepository;
    private final ObjectMapper objectMapper;

    public SkillMatchingService(ChatClient.Builder chatClientBuilder,
                                ResumeRepository resumeRepository,
                                ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.resumeRepository = resumeRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Match candidate resume with job requirements
     * Returns detailed match analysis with percentage, gaps, and recommendation
     */
    public SkillMatchResult matchJobRequirements(Long resumeId, String jobDescription) {
        try {
            log.info("Matching resume {} with job requirements", resumeId);

            // Fetch resume from database
            ResumeData resume = resumeRepository.findById(resumeId)
                    .orElseThrow(() -> new RuntimeException("Resume not found with ID: " + resumeId));

            // Build candidate profile summary
            String candidateProfile = buildCandidateProfile(resume);

            // Call AI for matching analysis
            String prompt = String.format("""
                    You are an expert technical recruiter. Analyze how well this candidate matches the job requirements.
                    
                    JOB REQUIREMENTS:
                    %s
                    
                    CANDIDATE PROFILE:
                    %s
                    
                    Provide a detailed analysis in JSON format (no markdown, just pure JSON):
                    {
                      "matchPercentage": 85,
                      "matchingSkills": ["skill1", "skill2"],
                      "missingSkills": ["skill3", "skill4"],
                      "strengths": ["strength1", "strength2"],
                      "concerns": ["concern1", "concern2"],
                      "recommendation": "STRONG_MATCH or GOOD_MATCH or WEAK_MATCH or NO_MATCH",
                      "reasoning": "brief explanation"
                    }
                    
                    Recommendations:
                    - STRONG_MATCH: 80-100%% match, hire immediately
                    - GOOD_MATCH: 60-79%% match, interview recommended
                    - WEAK_MATCH: 40-59%% match, consider if desperate
                    - NO_MATCH: <40%% match, reject
                    """, jobDescription, candidateProfile);

            String jsonResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.info("Match analysis completed");

            // Parse JSON response
            String cleanedJson = cleanJsonResponse(jsonResponse);
            JsonNode rootNode = objectMapper.readTree(cleanedJson);

            // Build SkillMatchResult
            SkillMatchResult result = new SkillMatchResult();
            result.setMatchPercentage(rootNode.path("matchPercentage").asInt(0));
            result.setRecommendation(rootNode.path("recommendation").asText("NO_MATCH"));
            result.setReasoning(rootNode.path("reasoning").asText(null));

            // Parse arrays
            result.setMatchingSkills(parseJsonArray(rootNode.path("matchingSkills")));
            result.setMissingSkills(parseJsonArray(rootNode.path("missingSkills")));
            result.setStrengths(parseJsonArray(rootNode.path("strengths")));
            result.setConcerns(parseJsonArray(rootNode.path("concerns")));

            return result;

        } catch (Exception e) {
            log.error("Error matching job requirements", e);
            throw new RuntimeException("Failed to match job requirements: " + e.getMessage(), e);
        }
    }

    /**
     * Suggest career improvements and skills to learn
     * AI analyzes current profile and suggests growth path
     */
    public CareerSuggestions suggestSkillImprovements(Long resumeId) {
        try {
            log.info("Generating skill improvement suggestions for resume {}", resumeId);

            // Fetch resume from database
            ResumeData resume = resumeRepository.findById(resumeId)
                    .orElseThrow(() -> new RuntimeException("Resume not found with ID: " + resumeId));

            // Build candidate profile summary
            String candidateProfile = buildCandidateProfile(resume);

            // Call AI for career suggestions
            String prompt = String.format("""
                    You are a senior career advisor for software engineers. Analyze this candidate's profile 
                    and suggest the top 5 skills they should learn next to advance their career.
                    
                    CANDIDATE PROFILE:
                    %s
                    
                    Provide suggestions in JSON format (no markdown, just pure JSON):
                    {
                      "suggestions": [
                        {
                          "skill": "skill name",
                          "priority": "HIGH or MEDIUM or LOW",
                          "timeToLearn": "estimated learning time",
                          "careerPath": "career path this enables",
                          "salaryImpact": "potential salary increase"
                        }
                      ]
                    }
                    
                    Focus on:
                    - In-demand skills in current market
                    - Natural progression from existing skills
                    - High ROI skills (salary impact)
                    - Skills that open new career paths
                    
                    Limit to 5 most impactful suggestions.
                    """, candidateProfile);

            String jsonResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.info("Skill suggestions generated");

            // Parse JSON response
            String cleanedJson = cleanJsonResponse(jsonResponse);
            JsonNode rootNode = objectMapper.readTree(cleanedJson);

            // Build CareerSuggestions
            CareerSuggestions careerSuggestions = new CareerSuggestions();
            List<CareerSuggestions.SkillSuggestion> suggestions = new ArrayList<>();

            JsonNode suggestionsNode = rootNode.path("suggestions");
            if (suggestionsNode.isArray()) {
                for (JsonNode suggestionNode : suggestionsNode) {
                    CareerSuggestions.SkillSuggestion suggestion = new CareerSuggestions.SkillSuggestion();
                    suggestion.setSkill(suggestionNode.path("skill").asText(null));
                    suggestion.setPriority(suggestionNode.path("priority").asText(null));
                    suggestion.setTimeToLearn(suggestionNode.path("timeToLearn").asText(null));
                    suggestion.setCareerPath(suggestionNode.path("careerPath").asText(null));
                    suggestion.setSalaryImpact(suggestionNode.path("salaryImpact").asText(null));
                    suggestions.add(suggestion);
                }
            }

            careerSuggestions.setSuggestions(suggestions);
            return careerSuggestions;

        } catch (Exception e) {
            log.error("Error generating skill suggestions", e);
            throw new RuntimeException("Failed to generate suggestions: " + e.getMessage(), e);
        }
    }

    /**
     * Build a summary of candidate profile for AI prompts
     */
    private String buildCandidateProfile(ResumeData resume) {
        StringBuilder profile = new StringBuilder();

        profile.append("Name: ").append(resume.getFullName()).append("\n");
        profile.append("Current Role: ").append(resume.getCurrentRole()).append("\n");
        profile.append("Total Experience: ").append(resume.getTotalExperience()).append("\n");

        if (resume.getSkills() != null && !resume.getSkills().isEmpty()) {
            profile.append("Skills: ").append(String.join(", ", resume.getSkills())).append("\n");
        }

        if (resume.getWorkExperience() != null && !resume.getWorkExperience().isEmpty()) {
            profile.append("\nWork Experience:\n");
            resume.getWorkExperience().forEach(exp -> {
                profile.append("- ").append(exp.getRole())
                        .append(" at ").append(exp.getCompany())
                        .append(" (").append(exp.getDuration()).append(")\n");
            });
        }

        if (resume.getEducation() != null && !resume.getEducation().isEmpty()) {
            profile.append("\nEducation:\n");
            resume.getEducation().forEach(edu -> {
                profile.append("- ").append(edu.getDegree())
                        .append(" from ").append(edu.getInstitution())
                        .append(" (").append(edu.getYear()).append(")\n");
            });
        }

        if (resume.getCertifications() != null && !resume.getCertifications().isEmpty()) {
            profile.append("\nCertifications: ")
                    .append(String.join(", ", resume.getCertifications())).append("\n");
        }

        return profile.toString();
    }

    /**
     * Parse JSON array to List<String>
     */
    private List<String> parseJsonArray(JsonNode arrayNode) {
        List<String> result = new ArrayList<>();
        if (arrayNode.isArray()) {
            for (JsonNode item : arrayNode) {
                result.add(item.asText());
            }
        }
        return result;
    }

    /**
     * Clean JSON response by removing markdown code blocks
     */
    private String cleanJsonResponse(String response) {
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }
}