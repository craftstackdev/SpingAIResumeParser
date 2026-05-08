package com.SpringResumeParser.ai_chatbot.dto;

import java.util.List;

public class SkillMatchResult {

    private int matchPercentage;
    private List<String> matchingSkills;
    private List<String> missingSkills;
    private List<String> strengths;
    private List<String> concerns;
    private String recommendation; // STRONG_MATCH, GOOD_MATCH, WEAK_MATCH, NO_MATCH
    private String reasoning;

    // Constructors
    public SkillMatchResult() {
    }

    // Getters and Setters
    public int getMatchPercentage() {
        return matchPercentage;
    }

    public void setMatchPercentage(int matchPercentage) {
        this.matchPercentage = matchPercentage;
    }

    public List<String> getMatchingSkills() {
        return matchingSkills;
    }

    public void setMatchingSkills(List<String> matchingSkills) {
        this.matchingSkills = matchingSkills;
    }

    public List<String> getMissingSkills() {
        return missingSkills;
    }

    public void setMissingSkills(List<String> missingSkills) {
        this.missingSkills = missingSkills;
    }

    public List<String> getStrengths() {
        return strengths;
    }

    public void setStrengths(List<String> strengths) {
        this.strengths = strengths;
    }

    public List<String> getConcerns() {
        return concerns;
    }

    public void setConcerns(List<String> concerns) {
        this.concerns = concerns;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    @Override
    public String toString() {
        return "SkillMatchResult{" +
                "matchPercentage=" + matchPercentage +
                ", recommendation='" + recommendation + '\'' +
                ", matchingSkills=" + matchingSkills +
                ", missingSkills=" + missingSkills +
                '}';
    }
}