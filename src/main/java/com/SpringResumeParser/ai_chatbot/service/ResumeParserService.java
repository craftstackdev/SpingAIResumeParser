package com.SpringResumeParser.ai_chatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.SpringResumeParser.ai_chatbot.entity.Education;
import com.SpringResumeParser.ai_chatbot.entity.ResumeData;
import com.SpringResumeParser.ai_chatbot.entity.WorkExperience;
import com.SpringResumeParser.ai_chatbot.repository.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResumeParserService {

    private static final Logger log = LoggerFactory.getLogger(ResumeParserService.class);

    private final ChatClient chatClient;
    private final ResumeRepository resumeRepository;
    private final ObjectMapper objectMapper;

    public ResumeParserService(ChatClient.Builder chatClientBuilder,
                               ResumeRepository resumeRepository,
                               ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.resumeRepository = resumeRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Parse resume image and extract structured data
     * Saves to database and returns the entity
     */
    public ResumeData parseResume(MultipartFile file) {
        try {
            log.info("Parsing resume: {}", file.getOriginalFilename());


            // Convert file to Resource
            Resource imageResource = new ByteArrayResource(
                    file.getBytes()
            ){
                @Override
                public String getFilename()
                {
                    return file.getOriginalFilename();
                }
            };


            String textPrompt = """
                    Extract ALL information from this resume image and return ONLY a valid JSON object.
                    
                    Return this exact structure (no markdown, no code blocks, just pure JSON):
                    {
                      "fullName": "candidate full name",
                      "email": "email address",
                      "phone": "phone number",
                      "currentRole": "current job title",
                      "totalExperience": "X years Y months",
                      "workExperience": [
                        {
                          "company": "company name",
                          "role": "job title",
                          "duration": "start - end or duration",
                          "description": "brief description"
                        }
                      ],
                      "education": [
                        {
                          "degree": "degree name",
                          "institution": "university/college name",
                          "year": "graduation year"
                        }
                      ],
                      "skills": ["skill1", "skill2", "skill3"],
                      "certifications": ["cert1", "cert2"]
                    }
                    
                    Important:
                    - Return ONLY the JSON object, no explanations
                    - Use empty arrays [] if sections are missing
                    - Use null for missing individual fields
                    - Extract ALL skills mentioned
                    - Include ALL work experiences
                    """;

            // Call GPT-4 Vision with structured extraction prompt

            String jsonResponse = chatClient.prompt()
                    .user(u ->u.text(textPrompt)
                            .media(MimeTypeUtils.IMAGE_PNG, imageResource)
                    )
                    .call()
                    .content();

            log.info("GPT-4 Vision response received, parsing JSON...");

            // Clean the response (remove markdown code blocks if present)
            String cleanedJson = cleanJsonResponse(jsonResponse);

            // Parse JSON response
            JsonNode rootNode = objectMapper.readTree(cleanedJson);

            // Create ResumeData entity
            ResumeData resumeData = new ResumeData();
            resumeData.setFullName(rootNode.path("fullName").asText(null));
            resumeData.setEmail(rootNode.path("email").asText(null));
            resumeData.setPhone(rootNode.path("phone").asText(null));
            resumeData.setCurrentRole(rootNode.path("currentRole").asText(null));
            resumeData.setTotalExperience(rootNode.path("totalExperience").asText(null));
            resumeData.setFileName(file.getOriginalFilename());
            resumeData.setUploadedAt(LocalDateTime.now());

            // Parse work experience
            List<WorkExperience> workExperiences = new ArrayList<>();
            JsonNode workNode = rootNode.path("workExperience");
            if (workNode.isArray()) {
                for (JsonNode work : workNode) {
                    WorkExperience exp = new WorkExperience();
                    exp.setCompany(work.path("company").asText(null));
                    exp.setRole(work.path("role").asText(null));
                    exp.setDuration(work.path("duration").asText(null));
                    exp.setDescription(work.path("description").asText(null));
                    exp.setResumeData(resumeData);
                    workExperiences.add(exp);
                }
            }
            resumeData.setWorkExperience(workExperiences);

            // Parse education
            List<Education> educationList = new ArrayList<>();
            JsonNode eduNode = rootNode.path("education");
            if (eduNode.isArray()) {
                for (JsonNode edu : eduNode) {
                    Education education = new Education();
                    education.setDegree(edu.path("degree").asText(null));
                    education.setInstitution(edu.path("institution").asText(null));
                    education.setYear(edu.path("year").asText(null));
                    education.setResumeData(resumeData);
                    educationList.add(education);
                }
            }
            resumeData.setEducation(educationList);

            // Parse skills
            List<String> skills = new ArrayList<>();
            JsonNode skillsNode = rootNode.path("skills");
            if (skillsNode.isArray()) {
                for (JsonNode skill : skillsNode) {
                    skills.add(skill.asText());
                }
            }
            resumeData.setSkills(skills);

            // Parse certifications
            List<String> certifications = new ArrayList<>();
            JsonNode certsNode = rootNode.path("certifications");
            if (certsNode.isArray()) {
                for (JsonNode cert : certsNode) {
                    certifications.add(cert.asText());
                }
            }
            resumeData.setCertifications(certifications);

            // Save to database
            ResumeData savedResume = resumeRepository.save(resumeData);
            log.info("Resume parsed and saved with ID: {}", savedResume.getId());

            return savedResume;

        } catch (Exception e) {
            log.error("Error parsing resume", e);
            throw new RuntimeException("Failed to parse resume: " + e.getMessage(), e);
        }
    }

    /**
     * Clean JSON response by removing markdown code blocks
     */
    private String cleanJsonResponse(String response) {
        // Remove markdown code blocks if present
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

    /**
     * Get all resumes from database
     */
    public List<ResumeData> getAllResumes() {
        return resumeRepository.findAll();
    }

    /**
     * Get resume by ID
     */
    public ResumeData getResumeById(Long id) {
        return resumeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Resume not found with ID: " + id));
    }

    /**
     * Delete resume by ID
     */
    public void deleteResume(Long id) {
        resumeRepository.deleteById(id);
        log.info("Resume deleted with ID: {}", id);
    }
}