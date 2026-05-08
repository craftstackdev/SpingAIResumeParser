package com.SpringResumeParser.ai_chatbot.controller;

import com.SpringResumeParser.ai_chatbot.service.DocumentService;
import com.SpringResumeParser.ai_chatbot.service.RAGService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

/**
 * MODULE 6: Documentation Controller - REST API for RAG Operations
 *
 * This controller exposes endpoints for the complete RAG workflow:
 *
 * Document Ingestion:
 * - POST /api/docs/index    → Index default documentation from classpath
 * - POST /api/docs/upload   → Upload and index custom documents
 *
 * RAG Query:
 * - POST /api/docs/ask      → Ask questions (retrieves context + generates answer)
 *
 * Vector Store Inspection (for debugging/learning):
 * - GET    /api/docs/vectors              → View stored vectors and embeddings
 * - GET    /api/docs/vectors/stats        → Get statistics about stored vectors
 * - DELETE /api/docs/vectors              → Clear all vectors
 * - DELETE /api/docs/vectors/source/{name} → Delete vectors from specific source
 *
 * The inspection endpoints are especially useful for understanding
 * how documents are chunked and stored as embeddings!
 */
@RestController
@RequestMapping("/api/docs")
@CrossOrigin(origins = "*")  // Allow requests from any origin (for development)
public class DocumentationController {

    private static final Logger log = LoggerFactory.getLogger(DocumentationController.class);

    @Autowired
    private DocumentService documentService;  // Handles document chunking and indexing

    @Autowired
    private RAGService ragService;  // Handles RAG queries (retrieve + generate)

    @Autowired
    private VectorStore vectorStore;  // Spring AI's vector store abstraction

    @Autowired
    private DataSource dataSource;  // Direct DB access for vector inspection

    // ========================================================================
    // DOCUMENT INGESTION ENDPOINTS
    // ========================================================================

    /**
     * Index default documentation from classpath.
     *
     * POST /api/docs/index
     *
     * This loads a predefined document from src/main/resources/docs/
     * and indexes it into the vector store.
     *
     * Useful for:
     * - Initial setup with sample documentation
     * - Demo purposes
     * - Testing the RAG pipeline
     *
     * @return JSON with success status and number of chunks created
     */
    @PostMapping("/index")
    public ResponseEntity<Map<String, Object>> indexDefaultDocumentation() {
        try {
            log.info("Indexing default documentation...");

            // Load and index the default document from classpath
            // The DocumentService handles: reading → chunking → embedding → storing
            int chunks = documentService.loadAndIndexDocument(
                    "classpath:docs/ecommerce-customer-support-policy.txt"
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Default documentation indexed successfully");
            response.put("chunks", chunks);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error indexing default documentation", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Upload and index a custom document.
     *
     * POST /api/docs/upload
     * Content-Type: multipart/form-data
     * Body: file=<your-file.txt>
     *
     * Accepts .txt and .md files, reads their content, and indexes
     * them into the vector store for RAG queries.
     *
     * Example using curl:
     * curl -X POST -F "file=@my-document.txt" http://localhost:8080/api/docs/upload
     *
     * @param file The uploaded file (must be .txt or .md)
     * @return JSON with success status, filename, and chunks created
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @RequestParam("file") MultipartFile file) {

        //document was converted to chunks
        //embedding model will be called
        //stored in vector db
        try {
            log.info("Uploading file: {}", file.getOriginalFilename());

            // Validate file extension
            // Only supporting plain text files for simplicity
            // For PDFs, use Spring AI's PDFReader
            // For Word docs, use TikaDocumentReader
            String filename = file.getOriginalFilename();
            if (filename == null ||
                    !(filename.endsWith(".txt") || filename.endsWith(".md"))) {

                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Only .txt and .md files are supported");
                return ResponseEntity.badRequest().body(response);
            }

            // Read file content as UTF-8 string
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);

            // Index the document (chunk → embed → store)
            int chunks = documentService.loadAndIndexDocumentFromString(
                    content,
                    filename
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Document indexed successfully");
            response.put("filename", filename);
            response.put("chunks", chunks);

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Error reading uploaded file", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", "Failed to read file: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        } catch (Exception e) {
            log.error("Error processing upload", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ========================================================================
    // RAG QUERY ENDPOINT
    // ========================================================================

    /**
     * Ask a question using RAG (Retrieval Augmented Generation).
     *
     * POST /api/docs/ask
     * Content-Type: application/json
     * Body: { "question": "What is your return policy?" }
     *
     * This is the main RAG endpoint that:
     * 1. Takes the user's question
     * 2. Searches vector store for relevant document chunks
     * 3. Sends context + question to LLM
     * 4. Returns the generated answer
     *
     * The answer is grounded in YOUR documents, not just LLM's training data!
     *
     * @param request JSON body containing "question" field
     * @return JSON with success status and the generated answer
     */
    @PostMapping("/ask")
    public ResponseEntity<Map<String, Object>> askQuestion(
            @RequestBody Map<String, String> request) {

        try {
            String question = request.get("question");
            if (question == null || question.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("error", "Question is required");
                return ResponseEntity.badRequest().body(response);
            }

            // Delegate to RAGService which handles:
            // similarity search → context building → LLM call
            String answer = ragService.askQuestion(question);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("answer", answer);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing question", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // ========================================================================
    // VECTOR INSPECTION ENDPOINTS (For Learning & Debugging)
    // ========================================================================
    // These endpoints let you "peek inside" the vector store to understand
    // how documents are stored and what embeddings look like.

    /**
     * Get all vectors from the database with embedding previews.
     *
     * GET /api/docs/vectors?limit=10&offset=0
     *
     * This endpoint is GREAT for learning because it shows:
     * - The actual text chunks stored
     * - Metadata attached to each chunk
     * - Preview of the embedding vectors (first/last 10 dimensions)
     * - Total embedding dimensions (usually 1536 for OpenAI)
     *
     * Embeddings are arrays of ~1536 floating-point numbers that
     * represent the "meaning" of the text in vector space.
     * Similar texts have similar embeddings (close in vector space).
     *
     * @param limit  Number of vectors to return (default: 10)
     * @param offset Pagination offset (default: 0)
     * @return JSON with vectors, embeddings preview, and metadata
     */
    @GetMapping("/vectors")
    public ResponseEntity<Map<String, Object>> getAllVectors(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "0") int offset) {

        try {
            log.info("Fetching vectors: limit={}, offset={}", limit, offset);

            List<Map<String, Object>> vectors = new ArrayList<>();

            // Direct JDBC query to access the raw vector_store table
            // This bypasses Spring AI's abstraction to show the actual data
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT id, content, metadata, embedding FROM vector_store ORDER BY id LIMIT ? OFFSET ?")) {

                stmt.setInt(1, limit);
                stmt.setInt(2, offset);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> vector = new LinkedHashMap<>();

                        // Unique ID for this chunk
                        vector.put("id", rs.getString("id"));

                        // The actual text content of this chunk
                        String content = rs.getString("content");
                        vector.put("content", content);
                        vector.put("content_preview", content.substring(
                                0, Math.min(200, content.length())
                        ) + "...");
                        vector.put("content_length", content.length());

                        // Metadata (source file, indexed_at, etc.)
                        vector.put("metadata", rs.getString("metadata"));

                        // Parse and preview the embedding vector
                        // Full embedding is too large to display (~1536 floats)
                        // So we show first 10 and last 10 dimensions
                        String embeddingStr = rs.getString("embedding");
                        if (embeddingStr != null) {
                            // Remove brackets and split into individual values
                            embeddingStr = embeddingStr.replace("[", "").replace("]", "");
                            String[] values = embeddingStr.split(",");

                            // Parse first 10 values for preview
                            // These are the first dimensions of the embedding vector
                            List<Float> first10 = new ArrayList<>();
                            for (int i = 0; i < Math.min(10, values.length); i++) {
                                try {
                                    first10.add(Float.parseFloat(values[i].trim()));
                                } catch (NumberFormatException e) {
                                    // Skip invalid values
                                }
                            }

                            // Parse last 10 values for preview
                            List<Float> last10 = new ArrayList<>();
                            int startLast = Math.max(0, values.length - 10);
                            for (int i = startLast; i < values.length; i++) {
                                try {
                                    last10.add(Float.parseFloat(values[i].trim()));
                                } catch (NumberFormatException e) {
                                    // Skip invalid values
                                }
                            }

                            // Show embedding dimensions (usually 1536 for OpenAI ada-002)
                            vector.put("embedding_dimensions", values.length);
                            vector.put("embedding_preview_first_10", first10);
                            vector.put("embedding_preview_last_10", last10);

                            // Optionally include full embedding (can be large!)
                            // Uncomment next line if you want the complete embedding array
                            // vector.put("embedding_full", embeddingStr);
                        }

                        vectors.add(vector);
                    }
                }
            }

            // Get total count for pagination info
            int totalCount = 0;
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM vector_store");
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    totalCount = rs.getInt(1);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("total_vectors", totalCount);
            response.put("limit", limit);
            response.put("offset", offset);
            response.put("returned_count", vectors.size());
            response.put("vectors", vectors);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching vectors", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Get statistics about stored vectors.
     *
     * GET /api/docs/vectors/stats
     *
     * Useful for understanding your vector store:
     * - Total number of chunks stored
     * - Average/min/max chunk sizes
     * - Which source documents have been indexed
     * - How many chunks per source
     *
     * This helps you tune your chunking strategy!
     *
     * @return JSON with vector statistics
     */
    @GetMapping("/vectors/stats")
    public ResponseEntity<Map<String, Object>> getVectorStats() {
        try {
            log.info("Fetching vector statistics");

            int totalCount = 0;
            double avgLength = 0;
            int minLength = 0;
            int maxLength = 0;

            try (Connection conn = dataSource.getConnection()) {
                // Get total count of vectors
                try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM vector_store");
                     ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        totalCount = rs.getInt(1);
                    }
                }

                // Get content length statistics
                // Helps understand if chunks are too big/small
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT AVG(LENGTH(content)), MIN(LENGTH(content)), MAX(LENGTH(content)) FROM vector_store");
                     ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        avgLength = rs.getDouble(1);
                        minLength = rs.getInt(2);
                        maxLength = rs.getInt(3);
                    }
                }
            }

            // Get unique source documents
            // Shows which files have been indexed
            List<String> sources = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT DISTINCT metadata->>'source' as source FROM vector_store");
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    sources.add(rs.getString("source"));
                }
            }

            // Get chunk count per source document
            // Helps understand how each document was split
            List<Map<String, Object>> chunksPerSource = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT metadata->>'source' as source, COUNT(*) as chunk_count FROM vector_store GROUP BY metadata->>'source' ORDER BY chunk_count DESC");
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("source", rs.getString("source"));
                    item.put("chunk_count", rs.getInt("chunk_count"));
                    chunksPerSource.add(item);
                }
            }

            Map<String, Object> stats = new HashMap<>();
            stats.put("avg_content_length", avgLength);
            stats.put("min_content_length", minLength);
            stats.put("max_content_length", maxLength);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("total_vectors", totalCount);
            response.put("unique_sources", sources.size());
            response.put("sources", sources);
            response.put("chunks_per_source", chunksPerSource);
            response.put("statistics", stats);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching vector stats", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Delete ALL vectors from the database.
     *
     * DELETE /api/docs/vectors
     *
     * CAUTION: This permanently removes all indexed documents!
     *
     * Useful for:
     * - Starting fresh with new documents
     * - Cleaning up during development
     * - Testing the full indexing flow
     *
     * @return JSON with success status and count of deleted vectors
     */
    @DeleteMapping("/vectors")
    public ResponseEntity<Map<String, Object>> deleteAllVectors() {
        try {
            log.info("Deleting all vectors from database");

            int countBefore = 0;
            int deletedCount = 0;

            try (Connection conn = dataSource.getConnection()) {
                // Get count before deletion (for reporting)
                try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM vector_store");
                     ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        countBefore = rs.getInt(1);
                    }
                }

                // Delete all vectors
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM vector_store")) {
                    deletedCount = stmt.executeUpdate();
                }
            }

            log.info("Deleted {} vectors from database", deletedCount);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "All vectors deleted successfully");
            response.put("deleted_count", deletedCount);
            response.put("count_before", countBefore);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error deleting vectors", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * Delete vectors from a specific source document.
     *
     * DELETE /api/docs/vectors/source/{filename}
     *
     * Example: DELETE /api/docs/vectors/source/old-policy.txt
     *
     * Useful for:
     * - Re-indexing a single document after updates
     * - Removing outdated documentation
     * - Selective cleanup without affecting other documents
     *
     * @param filename The source filename to delete (matches metadata.source)
     * @return JSON with success status and count of deleted vectors
     */
    @DeleteMapping("/vectors/source/{filename}")
    public ResponseEntity<Map<String, Object>> deleteVectorsBySource(
            @PathVariable String filename) {
        try {
            log.info("Deleting vectors for source: {}", filename);

            int countBefore = 0;
            int deletedCount = 0;

            try (Connection conn = dataSource.getConnection()) {
                // Get count before deletion for this specific source
                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT COUNT(*) FROM vector_store WHERE metadata->>'source' = ?")) {
                    stmt.setString(1, filename);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            countBefore = rs.getInt(1);
                        }
                    }
                }

                // Delete vectors matching this source
                // Uses PostgreSQL JSONB operator ->> to query metadata
                try (PreparedStatement stmt = conn.prepareStatement(
                        "DELETE FROM vector_store WHERE metadata->>'source' = ?")) {
                    stmt.setString(1, filename);
                    deletedCount = stmt.executeUpdate();
                }
            }

            log.info("Deleted {} vectors for source: {}", deletedCount, filename);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Vectors deleted successfully for source: " + filename);
            response.put("deleted_count", deletedCount);
            response.put("count_before", countBefore);
            response.put("source", filename);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error deleting vectors for source: {}", filename, e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(response);
        }
    }
}