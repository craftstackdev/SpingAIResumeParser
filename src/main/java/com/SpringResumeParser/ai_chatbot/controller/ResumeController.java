package com.SpringResumeParser.ai_chatbot.controller;

import com.SpringResumeParser.ai_chatbot.dto.JobDescriptionRequest;
import com.SpringResumeParser.ai_chatbot.dto.SkillMatchResult;
import com.SpringResumeParser.ai_chatbot.dto.CareerSuggestions;
import com.SpringResumeParser.ai_chatbot.entity.ResumeData;
import com.SpringResumeParser.ai_chatbot.service.VisionService;
import com.SpringResumeParser.ai_chatbot.service.ResumeParserService;
import com.SpringResumeParser.ai_chatbot.service.SkillMatchingService;
import com.SpringResumeParser.ai_chatbot.service.CandidateSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.SpringResumeParser.ai_chatbot.service.ImageValidationService;
import com.SpringResumeParser.ai_chatbot.service.ContentModerationService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/resume")
@CrossOrigin(origins = "*")
public class ResumeController {

    private static final Logger log = LoggerFactory.getLogger(ResumeController.class);

    private final VisionService visionService;
    private final ResumeParserService resumeParserService;
    private final SkillMatchingService skillMatchingService;
    private final CandidateSearchService candidateSearchService;
    private final ImageValidationService imageValidationService;
    private final ContentModerationService contentModerationService;

    public ResumeController(VisionService visionService,
                            ResumeParserService resumeParserService,
                            SkillMatchingService skillMatchingService,
                            CandidateSearchService candidateSearchService,
                            ImageValidationService imageValidationService,
                            ContentModerationService contentModerationService) {
        this.visionService = visionService;
        this.resumeParserService = resumeParserService;
        this.skillMatchingService = skillMatchingService;
        this.candidateSearchService = candidateSearchService;
        this.imageValidationService = imageValidationService;
        this.contentModerationService = contentModerationService;
    }

    /**
     * Endpoint 1: Quick analysis (summary only)
     * Uses GPT-4 Vision to provide a brief summary
     *
     * SECURITY LAYERS:
     * 1. File format validation (malware, file bombs)
     * 2. AI content moderation (nudity, violence, inappropriate content)
     * 3. Then processing
     */
    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeResume(@RequestParam("file") MultipartFile file) {
        try {
            log.info("Analyzing resume: {}", file.getOriginalFilename());

            // LAYER 1: Validate file format and security
            imageValidationService.validateImage(file);
            log.info("File validation passed for: {}", file.getOriginalFilename());

            // LAYER 2: AI content moderation (detect inappropriate content)
            ContentModerationService.ModerationResult moderation =
                    contentModerationService.moderateImage(file);

            if (!moderation.isSafe()) {
                log.warn("Content moderation FAILED for: {} - Category: {}, Reason: {}",
                        file.getOriginalFilename(),
                        moderation.getCategory(),
                        moderation.getReason());

                Map<String, Object> error = new HashMap<>();
                error.put("error", "Inappropriate content detected");
                error.put("type", "CONTENT_VIOLATION");
                error.put("category", moderation.getCategory());
                error.put("message", "This image contains inappropriate content and cannot be processed");
                return ResponseEntity.status(451).body(error); // 451 = Unavailable For Legal Reasons
            }

            log.info("Content moderation passed for: {}", file.getOriginalFilename());

            // LAYER 3: Process the image (now we know it's safe)
            String summary = visionService.analyzeResume(file);

            Map<String, Object> response = new HashMap<>();
            response.put("fileName", file.getOriginalFilename());
            response.put("summary", summary);
            response.put("message", "Resume analyzed successfully");

            return ResponseEntity.ok(response);

        } catch (ImageValidationService.ImageValidationException e) {
            log.warn("Image validation failed: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid image: " + e.getMessage());
            error.put("type", "VALIDATION_ERROR");
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Error analyzing resume", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to analyze resume: " + e.getMessage());
            error.put("type", "PROCESSING_ERROR");
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Endpoint 2: Full structured parsing and save to database
     * Extracts all fields and persists to PostgreSQL
     */
    @PostMapping("/parse")
    public ResponseEntity<?> parseResume(@RequestParam("file") MultipartFile file) {
        try {
            log.info("Parsing resume: {}", file.getOriginalFilename());

            // LAYER 1: Validate file format
            imageValidationService.validateImage(file);
            log.info("File validation passed for: {}", file.getOriginalFilename());

            // LAYER 2: AI content moderation
            ContentModerationService.ModerationResult moderation =
                    contentModerationService.moderateImage(file);

            if (!moderation.isSafe()) {
                log.warn("Content moderation FAILED for: {} - Category: {}, Reason: {}",
                        file.getOriginalFilename(),
                        moderation.getCategory(),
                        moderation.getReason());

                Map<String, Object> error = new HashMap<>();
                error.put("error", "Inappropriate content detected");
                error.put("type", "CONTENT_VIOLATION");
                error.put("category", moderation.getCategory());
                error.put("message", "This image contains inappropriate content and cannot be processed");
                return ResponseEntity.status(451).body(error);
            }

            log.info("Content moderation passed for: {}", file.getOriginalFilename());

            ResumeData resumeData = resumeParserService.parseResume(file);

            log.info("Resume parsed and saved with ID: {}", resumeData.getId());
            return ResponseEntity.ok(resumeData);
        } catch (ImageValidationService.ImageValidationException e) {
            log.warn("Image validation failed: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid image: " + e.getMessage());
            error.put("type", "VALIDATION_ERROR");
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            log.error("Error parsing resume", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to parse resume: " + e.getMessage());
            error.put("type", "PROCESSING_ERROR");
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Endpoint 3: Match resume with job requirements
     * Analyzes candidate fit for a specific job
     */
    @PostMapping("/{id}/match-job")
    public ResponseEntity<?> matchJob(@PathVariable Long id,
                                      @RequestBody JobDescriptionRequest request) {
        try {
            log.info("Matching resume {} with job", id);
            SkillMatchResult matchResult = skillMatchingService.matchJobRequirements(id, request.getJobDescription());

            return ResponseEntity.ok(matchResult);
        } catch (Exception e) {
            log.error("Error matching job", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to match job: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Endpoint 4: Get career improvement suggestions
     * AI suggests which skills to learn next
     */
    @GetMapping("/{id}/suggest-skills")
    public ResponseEntity<?> suggestSkills(@PathVariable Long id) {
        try {
            log.info("Getting skill suggestions for resume {}", id);
            CareerSuggestions suggestions = skillMatchingService.suggestSkillImprovements(id);

            return ResponseEntity.ok(suggestions);
        } catch (Exception e) {
            log.error("Error suggesting skills", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to suggest skills: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Endpoint 5: Search candidates by skills
     * Find candidates who have specific skills
     */
    @GetMapping("/search/skills")
    public ResponseEntity<?> searchBySkills(@RequestParam List<String> skills,
                                            @RequestParam(defaultValue = "1") int minimumMatches) {
        try {
            log.info("Searching candidates with skills: {}, minimum matches: {}", skills, minimumMatches);
            List<ResumeData> candidates = candidateSearchService.searchBySkills(skills, minimumMatches);

            return ResponseEntity.ok(candidates);
        } catch (Exception e) {
            log.error("Error searching by skills", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to search by skills: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Endpoint 6: Search candidates by experience and role
     * Filter by minimum years of experience and role keywords
     */
    @GetMapping("/search/experience")
    public ResponseEntity<?> searchByExperience(@RequestParam String minExperience,
                                                @RequestParam(required = false) String role) {
        try {
            log.info("Searching candidates with min experience: {}, role: {}", minExperience, role);
            List<ResumeData> candidates = candidateSearchService.searchByExperienceAndRole(minExperience, role);

            return ResponseEntity.ok(candidates);
        } catch (Exception e) {
            log.error("Error searching by experience", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to search by experience: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Endpoint 7: Get all resumes
     */
    @GetMapping
    public ResponseEntity<?> getAllResumes() {
        try {
            log.info("Fetching all resumes");
            List<ResumeData> resumes = resumeParserService.getAllResumes();
            return ResponseEntity.ok(resumes);
        } catch (Exception e) {
            log.error("Error fetching resumes", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to fetch resumes: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Endpoint 8: Get resume by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getResumeById(@PathVariable Long id) {
        try {
            log.info("Fetching resume with ID: {}", id);
            ResumeData resume = resumeParserService.getResumeById(id);
            return ResponseEntity.ok(resume);
        } catch (Exception e) {
            log.error("Error fetching resume", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Resume not found with ID: " + id);
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Endpoint 9: Delete resume
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteResume(@PathVariable Long id) {
        try {
            log.info("Deleting resume with ID: {}", id);
            resumeParserService.deleteResume(id);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Resume deleted successfully");
            response.put("id", String.valueOf(id));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error deleting resume", e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Failed to delete resume: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Endpoint 10: Health check
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Resume Parser API");
        return ResponseEntity.ok(response);
    }
}