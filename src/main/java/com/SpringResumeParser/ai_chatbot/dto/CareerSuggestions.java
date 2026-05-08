package com.SpringResumeParser.ai_chatbot.dto;

import java.util.List;

public class CareerSuggestions {

    private List<SkillSuggestion> suggestions;

    // Constructors
    public CareerSuggestions() {
    }

    // Getters and Setters
    public List<SkillSuggestion> getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(List<SkillSuggestion> suggestions) {
        this.suggestions = suggestions;
    }

    // Nested class for individual skill suggestion
    public static class SkillSuggestion {
        private String skill;
        private String priority; // HIGH, MEDIUM, LOW
        private String timeToLearn;
        private String careerPath;
        private String salaryImpact;

        // Constructors
        public SkillSuggestion() {
        }

        // Getters and Setters
        public String getSkill() {
            return skill;
        }

        public void setSkill(String skill) {
            this.skill = skill;
        }

        public String getPriority() {
            return priority;
        }

        public void setPriority(String priority) {
            this.priority = priority;
        }

        public String getTimeToLearn() {
            return timeToLearn;
        }

        public void setTimeToLearn(String timeToLearn) {
            this.timeToLearn = timeToLearn;
        }

        public String getCareerPath() {
            return careerPath;
        }

        public void setCareerPath(String careerPath) {
            this.careerPath = careerPath;
        }

        public String getSalaryImpact() {
            return salaryImpact;
        }

        public void setSalaryImpact(String salaryImpact) {
            this.salaryImpact = salaryImpact;
        }

        @Override
        public String toString() {
            return "SkillSuggestion{" +
                    "skill='" + skill + '\'' +
                    ", priority='" + priority + '\'' +
                    ", timeToLearn='" + timeToLearn + '\'' +
                    ", careerPath='" + careerPath + '\'' +
                    ", salaryImpact='" + salaryImpact + '\'' +
                    '}';
        }
    }

    @Override
    public String toString() {
        return "CareerSuggestions{" +
                "suggestions=" + suggestions +
                '}';
    }
}