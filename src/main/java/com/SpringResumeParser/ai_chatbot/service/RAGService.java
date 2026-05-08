package com.SpringResumeParser.ai_chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MODULE 6: RAG (Retrieval Augmented Generation) Service
 *
 * RAG is a technique that enhances LLM responses by retrieving relevant
 * information from your own data before generating an answer.
 *
 * Why RAG?
 * - LLMs have knowledge cutoff dates (don't know recent events)
 * - LLMs don't know your private/company data
 * - RAG lets you "teach" the LLM your custom knowledge
 *
 * How it works:
 * 1. User asks a question
 * 2. Search vector store for relevant document chunks
 * 3. Inject retrieved context into the prompt
 * 4. LLM generates answer based on YOUR data
 *
 * This implementation uses MANUAL RAG (without QuestionAnswerAdvisor)
 * for better understanding of the underlying process.
 */
@Service
public class RAGService {

    private static final Logger log = LoggerFactory.getLogger(RAGService.class);

    private final ChatClient chatClient;
    private final VectorStore vectorStore;  // Stores document embeddings for similarity search

    /**
     * Constructor - Initializes ChatClient and VectorStore
     *
     * @param chatClientBuilder Spring AI's builder for creating ChatClient instances
     * @param vectorStore       The vector database containing your document embeddings
     *                          (e.g., PGVector, Pinecone, Chroma, etc.)
     */
    public RAGService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        log.info("=== Initializing RAG Service ===");

        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;

        log.info("✅ RAG Service initialized successfully");
    }

    /**
     * Process a question using RAG (Retrieval Augmented Generation)
     *
     * The RAG Pipeline:
     * ┌─────────────────────────────────────────────────────────────────┐
     * │  User Question                                                  │
     * │       ↓                                                         │
     * │  Vector Store Search (find similar document chunks)             │
     * │       ↓                                                         │
     * │  Build Context (combine relevant chunks)                        │
     * │       ↓                                                         │
     * │  Create Prompt (system prompt + context + question)             │
     * │       ↓                                                         │
     * │  LLM Generates Answer (grounded in YOUR data)                   │
     * └─────────────────────────────────────────────────────────────────┘
     *
     * @param question The user's question
     * @return LLM-generated answer based on retrieved context
     */
    public String askQuestion(String question) {
        log.info("📝 RAG Question: {}", question);

        if (question == null || question.trim().isEmpty()) {
            return "Please provide a question.";
        }

        try {
            // ================================================================
            // STEP 1: SIMILARITY SEARCH
            // ================================================================
            // Convert the question to an embedding (vector) and find the most
            // similar document chunks in the vector store.
            //
            // Parameters:
            // - query: The user's question (will be converted to embedding)
            // - topK: Number of similar documents to retrieve
            // - similarityThreshold: Minimum similarity score (0.0 to 1.0)
            //   Higher = more strict, Lower = more lenient
            List<Document> relevantDocs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(question)
                            .topK(1)
                            .similarityThreshold(0.7)
                            .build()
            );

            // ================================================================
            // STEP 2: CHECK RETRIEVAL RESULTS
            // ================================================================
            // If no documents meet the similarity threshold, we shouldn't
            // hallucinate - instead, honestly tell the user we don't know.
            if (relevantDocs.isEmpty()) {
                log.warn("No relevant documents found");
                return "I don't have information about that in my knowledge base.";
            }

            log.info("Found {} relevant document(s)", relevantDocs.size());

            // ================================================================
            // STEP 3: BUILD CONTEXT
            // ================================================================
            // Combine all retrieved document chunks into a single context string.
            // This context will be injected into the prompt so the LLM can
            // reference it when generating the answer.
            String context = relevantDocs.stream()
                    .map(Document::getFormattedContent)  // Get text content of each chunk
                    .collect(Collectors.joining("\n\n")); // Join with blank lines

            // ================================================================
            // STEP 4: CREATE SYSTEM PROMPT WITH CONTEXT
            // ================================================================
            // The system prompt instructs the LLM to:
            // 1. Only use the provided context to answer
            // 2. Admit when the answer isn't in the context
            //
            // This prevents hallucination and keeps answers grounded in YOUR data.
            String systemPrompt = """
                You are a helpful assistant. Answer the user's question based ONLY on the context provided below.
                If the answer is not in the context, say "I don't have information about that."
                
                Context:
                %s
                """.formatted(context);

            log.info(systemPrompt);

            // ================================================================
            // STEP 5: CALL LLM WITH CONTEXT + QUESTION
            // ================================================================
            // Now the LLM has:
            // - System prompt with instructions + retrieved context
            // - User's original question
            //
            // The LLM generates an answer grounded in your documents!
            String answer = chatClient.prompt()
                    .system(systemPrompt)  // Instructions + context
                    .user(question)        // Original question
                    .call()
                    .content();

            log.info("✅ Answer generated successfully");
            return answer;

        } catch (Exception e) {
            log.error("Error during RAG query", e);
            return "I'm unable to answer that question right now.";
        }
    }
}