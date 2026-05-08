package com.SpringResumeParser.ai_chatbot.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "work_experience")
public class WorkExperience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company")
    private String company;

    @Column(name = "role")
    private String role;

    @Column(name = "duration")
    private String duration;

    @Column(name = "description", length = 1000)
    private String description;

    // Many-to-One: Many work experiences belong to one resume
    @ManyToOne
    @JoinColumn(name = "resume_id")
    @JsonIgnore // Prevent circular reference in JSON serialization
    private ResumeData resumeData;

    // Constructors
    public WorkExperience() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ResumeData getResumeData() {
        return resumeData;
    }

    public void setResumeData(ResumeData resumeData) {
        this.resumeData = resumeData;
    }

    @Override
    public String toString() {
        return "WorkExperience{" +
                "id=" + id +
                ", company='" + company + '\'' +
                ", role='" + role + '\'' +
                ", duration='" + duration + '\'' +
                '}';
    }
}