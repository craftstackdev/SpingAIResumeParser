package com.SpringResumeParser.ai_chatbot.repository;

import com.SpringResumeParser.ai_chatbot.entity.ResumeData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ResumeRepository extends JpaRepository<ResumeData, Long> {
    // JpaRepository provides built-in methods:
    // - save(entity)
    // - findById(id)
    // - findAll()
    // - deleteById(id)
    // - count()
    // etc.

    // Custom queries can be added here if needed in future
    // Example:
    // List<ResumeData> findByCurrentRoleContaining(String role);
    // List<ResumeData> findBySkillsContaining(String skill);
}