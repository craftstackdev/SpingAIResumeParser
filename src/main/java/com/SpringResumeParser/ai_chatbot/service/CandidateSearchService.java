package com.SpringResumeParser.ai_chatbot.service;

import com.SpringResumeParser.ai_chatbot.entity.ResumeData;
import com.SpringResumeParser.ai_chatbot.repository.ResumeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CandidateSearchService {

    private static final Logger log = LoggerFactory.getLogger(CandidateSearchService.class);

    private final ResumeRepository resumeRepository;

    public CandidateSearchService(ResumeRepository resumeRepository) {
        this.resumeRepository = resumeRepository;
    }

    /**
     * Search candidates by required skills
     * Returns candidates who have at least minimumMatches of the required skills
     */
    public List<ResumeData> searchBySkills(List<String> requiredSkills, int minimumMatches) {
        log.info("Searching candidates with skills: {}, minimum matches: {}", requiredSkills, minimumMatches);

        // Fetch all resumes
        List<ResumeData> allResumes = resumeRepository.findAll();

        // Filter candidates based on skill matches
        List<ResumeData> matchingCandidates = allResumes.stream()
                .filter(resume -> {
                    if (resume.getSkills() == null || resume.getSkills().isEmpty()) {
                        return false;
                    }

                    // Count how many required skills the candidate has
                    long matchCount = requiredSkills.stream()
                            .filter(requiredSkill -> resume.getSkills().stream()
                                    .anyMatch(candidateSkill ->
                                            candidateSkill.toLowerCase().contains(requiredSkill.toLowerCase())))
                            .count();

                    return matchCount >= minimumMatches;
                })
                .sorted((r1, r2) -> {
                    // Sort by number of matching skills (descending)
                    long matches1 = countMatchingSkills(r1, requiredSkills);
                    long matches2 = countMatchingSkills(r2, requiredSkills);
                    return Long.compare(matches2, matches1);
                })
                .collect(Collectors.toList());

        log.info("Found {} matching candidates", matchingCandidates.size());
        return matchingCandidates;
    }

    /**
     * Search candidates by experience and role
     * Filters by minimum years of experience and role keywords
     */
    public List<ResumeData> searchByExperienceAndRole(String minExperience, String roleKeyword) {
        log.info("Searching candidates with min experience: {}, role: {}", minExperience, roleKeyword);

        // Fetch all resumes
        List<ResumeData> allResumes = resumeRepository.findAll();

        // Parse minimum experience (e.g., "3 years" -> 3)
        int minYears = extractYearsFromString(minExperience);

        // Filter candidates
        List<ResumeData> matchingCandidates = allResumes.stream()
                .filter(resume -> {
                    // Filter by experience
                    if (resume.getTotalExperience() != null) {
                        int candidateYears = extractYearsFromString(resume.getTotalExperience());
                        if (candidateYears < minYears) {
                            return false;
                        }
                    }

                    // Filter by role (if provided)
                    if (roleKeyword != null && !roleKeyword.trim().isEmpty()) {
                        if (resume.getCurrentRole() == null) {
                            return false;
                        }
                        return resume.getCurrentRole().toLowerCase()
                                .contains(roleKeyword.toLowerCase());
                    }

                    return true;
                })
                .sorted((r1, r2) -> {
                    // Sort by total experience (descending)
                    int exp1 = extractYearsFromString(r1.getTotalExperience());
                    int exp2 = extractYearsFromString(r2.getTotalExperience());
                    return Integer.compare(exp2, exp1);
                })
                .collect(Collectors.toList());

        log.info("Found {} matching candidates", matchingCandidates.size());
        return matchingCandidates;
    }

    /**
     * Count how many required skills a candidate has
     */
    private long countMatchingSkills(ResumeData resume, List<String> requiredSkills) {
        if (resume.getSkills() == null) {
            return 0;
        }

        return requiredSkills.stream()
                .filter(requiredSkill -> resume.getSkills().stream()
                        .anyMatch(candidateSkill ->
                                candidateSkill.toLowerCase().contains(requiredSkill.toLowerCase())))
                .count();
    }

    /**
     * Extract years from experience string
     * Examples: "5 years" -> 5, "3 years 6 months" -> 3, "2.5 years" -> 2
     */
    private int extractYearsFromString(String experienceStr) {
        if (experienceStr == null || experienceStr.trim().isEmpty()) {
            return 0;
        }

        try {
            // Try to extract first number from string
            String cleaned = experienceStr.toLowerCase()
                    .replaceAll("[^0-9.]", " ")
                    .trim();

            if (cleaned.isEmpty()) {
                return 0;
            }

            String[] parts = cleaned.split("\\s+");
            if (parts.length > 0 && !parts[0].isEmpty()) {
                return (int) Double.parseDouble(parts[0]);
            }
        } catch (Exception e) {
            log.warn("Could not parse experience: {}", experienceStr);
        }

        return 0;
    }
}