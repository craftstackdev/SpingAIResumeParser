package com.SpringResumeParser.ai_chatbot.dto;

public class JobDescriptionRequest {

    private String jobDescription;

    // Constructors
    public JobDescriptionRequest() {
    }

    public JobDescriptionRequest(String jobDescription) {
        this.jobDescription = jobDescription;
    }

    // Getters and Setters
    public String getJobDescription() {
        return jobDescription;
    }

    public void setJobDescription(String jobDescription) {
        this.jobDescription = jobDescription;
    }

    @Override
    public String toString() {
        return "JobDescriptionRequest{" +
                "jobDescription='" + jobDescription + '\'' +
                '}';
    }
}