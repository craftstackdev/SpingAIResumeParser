package com.SpringResumeParser.ai_chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MODULE 6: Document Service - Prepares documents for RAG
 *
 * This service handles the "ingestion" phase of RAG:
 * 1. Load documents (from string or file)
 * 2. Split into smaller chunks
 * 3. Store chunks in vector database (as embeddings)
 *
 * Why chunking?
 * - LLMs have token limits (can't process entire books at once)
 * - Smaller chunks = more precise similarity search
 * - Better retrieval = better answers
 *
 * The Document Ingestion Pipeline:
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  Raw Document (PDF, TXT, etc.)                                  │
 * │       ↓                                                         │
 * │  Load & Parse (TextReader, PDFReader, etc.)                     │
 * │       ↓                                                         │
 * │  Split into Chunks (TokenTextSplitter)                          │
 * │       ↓                                                         │
 * │  Generate Embeddings (automatic via VectorStore)                │
 * │       ↓                                                         │
 * │  Store in Vector Database (PGVector, Pinecone, etc.)            │
 * └─────────────────────────────────────────────────────────────────┘
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    // VectorStore is AUTO-INJECTED by Spring Boot based on your configuration
    // (e.g., PGVectorStore if you have spring-ai-pgvector-store-spring-boot-starter)
    private final VectorStore vectorStore;

    // ResourceLoader helps load files from classpath, filesystem, or URLs
    private final ResourceLoader resourceLoader;

    /**
     * Constructor - Dependencies are AUTO-INJECTED by Spring Boot!
     *
     * @param vectorStore     Auto-configured based on your dependencies
     *                        (PGVector, Chroma, Pinecone, etc.)
     * @param resourceLoader  Spring's utility to load resources from various locations
     */
    public DocumentService(VectorStore vectorStore, ResourceLoader resourceLoader) {
        this.vectorStore = vectorStore;
        this.resourceLoader = resourceLoader;
    }

    /**
     * Index a document from raw string content.
     *
     * Use this when you have document content as a String
     * (e.g., from API response, database, user input).
     *
     * @param content  The raw text content to index
     * @param filename A name to identify this document in metadata
     * @return Number of chunks created and indexed
     */
    public int loadAndIndexDocumentFromString(String content, String filename) {
        try {
            log.info("Indexing document: {}", filename);

            // ================================================================
            // STEP 1: CREATE DOCUMENT WITH METADATA
            // ================================================================
            // Metadata is useful for:
            // - Filtering search results by source
            // - Tracking when documents were indexed
            // - Adding any custom attributes (author, category, etc.)
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("source", filename);
            metadata.put("indexed_at", System.currentTimeMillis());

            Document document = new Document(content, metadata);

            // ================================================================
            // STEP 2: SPLIT DOCUMENT INTO CHUNKS
            // ================================================================
            // TokenTextSplitter breaks large documents into smaller pieces.
            //
            // Why chunk?
            // - LLMs have context limits (can't process huge documents)
            // - Smaller chunks = more precise similarity matching
            // - Retrieve only relevant parts, not entire documents
            //
            // Default: TokenTextSplitter splitter = new TokenTextSplitter();
            //
            // Custom configuration parameters:
            TokenTextSplitter splitter = new TokenTextSplitter(
                    500,   // chunkSize - Target 500 tokens per chunk
                    // (larger = more context, smaller = more precise)
                    350,   // minChunkSizeChars - Don't create chunks smaller than 350 chars
                    // (avoids tiny useless chunks)
                    5,     // minChunkLengthToEmbed - Skip chunks with fewer than 5 tokens
                    // (filters out noise)
                    10000, // maxNumChunks - Safety limit to prevent memory issues
                    true   // keepSeparator - Preserve paragraph breaks for readability
            );

            List<Document> chunks = splitter.apply(List.of(document));

            log.info("Split into {} chunks", chunks.size());

            // ================================================================
            // STEP 3: ADD CHUNKS TO VECTOR STORE
            // ================================================================
            // This single line does A LOT behind the scenes:
            // 1. Sends each chunk to embedding model (e.g., OpenAI text-embedding-ada-002)
            // 2. Converts text to vector (array of ~1500 numbers)
            // 3. Stores vector + text + metadata in database
            //
            // Now these chunks are searchable via similarity search!
            vectorStore.add(chunks);

            log.info("Indexed {} chunks from {}", chunks.size(), filename);
            return chunks.size();

        } catch (Exception e) {
            log.error("Error indexing document: {}", filename, e);
            throw new RuntimeException("Failed to index document: " + e.getMessage(), e);
        }
    }

    /**
     * Index a document from classpath or filesystem.
     *
     * Use this when you have document files (e.g., in resources folder).
     *
     * Supported resource paths:
     * - "classpath:documents/faq.txt"     - From src/main/resources
     * - "file:/path/to/document.txt"      - From filesystem
     * - "https://example.com/doc.txt"     - From URL
     *
     * @param resourcePath Path to the document resource
     * @return Number of chunks created and indexed
     */
    public int loadAndIndexDocument(String resourcePath) {
        try {
            log.info("Loading document from: {}", resourcePath);

            // ================================================================
            // STEP 1: LOAD DOCUMENT FROM RESOURCE PATH
            // ================================================================
            // ResourceLoader handles various protocols:
            // - classpath: for files in resources folder
            // - file: for filesystem paths
            // - http/https: for remote URLs
            Resource resource = resourceLoader.getResource(resourcePath);

            // TextReader reads plain text files
            // For other formats, use:
            // - PDFReader for PDF files
            // - JsonReader for JSON
            // - TikaDocumentReader for multiple formats (Word, Excel, etc.)
            TextReader textReader = new TextReader(resource);
            List<Document> documents = textReader.get();

            if (documents.isEmpty()) {
                log.warn("No documents found at: {}", resourcePath);
                return 0;
            }

            // ================================================================
            // STEP 2: ADD METADATA
            // ================================================================
            // Store the source path so we can track where chunks came from
            for (Document doc : documents) {
                doc.getMetadata().put("source_path", resourcePath);
            }

            // ================================================================
            // STEP 3: SPLIT INTO CHUNKS
            // ================================================================
            // Same chunking strategy as loadAndIndexDocumentFromString()
            //
            // Tip: You could extract this to a shared method or make
            // chunk size configurable via application.properties
            TokenTextSplitter splitter = new TokenTextSplitter(
                    500,   // chunkSize - 500 tokens per chunk
                    350,   // minChunkSizeChars - 350 min characters
                    5,     // minChunkLengthToEmbed - 5 min length
                    10000, // maxNumChunks - 10000 max chunks
                    true   // keepSeparator - keep paragraph breaks
            );

            List<Document> chunks = splitter.apply(documents);

            // ================================================================
            // STEP 4: STORE IN VECTOR DATABASE
            // ================================================================
            // Each chunk is embedded and stored for similarity search
            vectorStore.add(chunks);

            log.info("Successfully indexed {} chunks from {}", chunks.size(), resourcePath);
            return chunks.size();

        } catch (Exception e) {
            log.error("Error loading document: {}", resourcePath, e);
            throw new RuntimeException("Failed to load document: " + e.getMessage(), e);
        }
    }
}