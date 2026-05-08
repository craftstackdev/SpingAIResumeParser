package com.SpringResumeParser.ai_chatbot.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "education")
public class Education {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "degree")
    private String degree;

    @Column(name = "institution")
    private String institution;

    @Column(name = "year")
    private String year;

    // Many-to-One: Many education entries belong to one resume
    @ManyToOne
    @JoinColumn(name = "resume_id")
    @JsonIgnore // Prevent circular reference in JSON serialization
    private ResumeData resumeData;

    // Constructors
    public Education() {
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDegree() {
        return degree;
    }

    public void setDegree(String degree) {
        this.degree = degree;
    }

    public String getInstitution() {
        return institution;
    }

    public void setInstitution(String institution) {
        this.institution = institution;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public ResumeData getResumeData() {
        return resumeData;
    }

    public void setResumeData(ResumeData resumeData) {
        this.resumeData = resumeData;
    }

    @Override
    public String toString() {
        return "Education{" +
                "id=" + id +
                ", degree='" + degree + '\'' +
                ", institution='" + institution + '\'' +
                ", year='" + year + '\'' +
                '}';
    }
}