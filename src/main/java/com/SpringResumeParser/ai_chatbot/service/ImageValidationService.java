package com.SpringResumeParser.ai_chatbot.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Image Validation Service - Comprehensive security guardrails for image uploads
 *
 * Protects against:
 * 1. File type attacks (malicious extensions)
 * 2. Content type spoofing
 * 3. File bombs (huge files)
 * 4. Image bombs (malformed images)
 * 5. Executable content disguised as images
 * 6. Empty or corrupted files
 *
 * @author Karthik
 */
@Service
public class ImageValidationService {

    // Security Configuration
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int MAX_IMAGE_WIDTH = 4096;
    private static final int MAX_IMAGE_HEIGHT = 4096;
    private static final long MIN_FILE_SIZE = 100; // 100 bytes

    // Allowed MIME types
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/gif",
            "image/webp"
    );

    // Allowed file extensions
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp"
    );

    // File signatures (magic bytes) for common image formats
    private static final List<byte[]> IMAGE_SIGNATURES = Arrays.asList(
            new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, // JPEG
            new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47},         // PNG
            new byte[]{0x47, 0x49, 0x46, 0x38},                // GIF
            new byte[]{0x52, 0x49, 0x46, 0x46}                 // WEBP (RIFF)
    );

    /**
     * Comprehensive validation of uploaded image file.
     *
     * This is the main method to call - it runs ALL security checks.
     *
     * @param file The uploaded file
     * @throws ImageValidationException if validation fails
     */
    public void validateImage(MultipartFile file) throws ImageValidationException {
        // Layer 1: Basic checks
        validateNotNull(file);
        validateNotEmpty(file);

        // Layer 2: Size checks
        validateFileSize(file);

        // Layer 3: File type checks
        validateExtension(file);
        validateMimeType(file);

        // Layer 4: Content validation
        validateFileSignature(file);
        validateImageContent(file);

        // Layer 5: Dimension checks
        validateImageDimensions(file);
    }

    /**
     * Layer 1: Check file is not null
     */
    private void validateNotNull(MultipartFile file) throws ImageValidationException {
        if (file == null) {
            throw new ImageValidationException("File cannot be null");
        }
    }

    /**
     * Layer 1: Check file is not empty
     */
    private void validateNotEmpty(MultipartFile file) throws ImageValidationException {
        if (file.isEmpty()) {
            throw new ImageValidationException("File cannot be empty");
        }

        if (file.getSize() < MIN_FILE_SIZE) {
            throw new ImageValidationException(
                    "File too small. Minimum size: " + MIN_FILE_SIZE + " bytes"
            );
        }
    }

    /**
     * Layer 2: Validate file size (prevent file bombs)
     */
    private void validateFileSize(MultipartFile file) throws ImageValidationException {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ImageValidationException(
                    "File too large. Maximum size: " + (MAX_FILE_SIZE / 1024 / 1024) + "MB"
            );
        }
    }

    /**
     * Layer 3: Validate file extension (prevent .exe, .sh, etc.)
     */
    private void validateExtension(MultipartFile file) throws ImageValidationException {
        String filename = file.getOriginalFilename();

        if (filename == null || filename.isEmpty()) {
            throw new ImageValidationException("Filename cannot be empty");
        }

        // Check for double extensions like image.jpg.exe
        String[] parts = filename.split("\\.");
        if (parts.length > 2) {
            throw new ImageValidationException(
                    "Invalid filename format. Multiple extensions detected: " + filename
            );
        }

        String extension = getFileExtension(filename);

        if (extension.isEmpty()) {
            throw new ImageValidationException("File must have an extension");
        }

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ImageValidationException(
                    "Invalid file type. Allowed: " + ALLOWED_EXTENSIONS + ", Got: " + extension
            );
        }
    }

    /**
     * Layer 3: Validate MIME type (prevent content-type spoofing)
     */
    private void validateMimeType(MultipartFile file) throws ImageValidationException {
        String contentType = file.getContentType();

        if (contentType == null || contentType.isEmpty()) {
            throw new ImageValidationException("Content type cannot be empty");
        }

        if (!ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new ImageValidationException(
                    "Invalid content type. Allowed: " + ALLOWED_MIME_TYPES + ", Got: " + contentType
            );
        }
    }

    /**
     * Layer 4: Validate file signature/magic bytes
     *
     * This prevents attackers from renaming malicious files to .jpg
     * We check the actual file header bytes to verify it's a real image.
     */
    private void validateFileSignature(MultipartFile file) throws ImageValidationException {
        try {
            byte[] fileBytes = file.getBytes();

            if (fileBytes.length < 4) {
                throw new ImageValidationException("File too small to validate signature");
            }

            boolean signatureMatch = IMAGE_SIGNATURES.stream()
                    .anyMatch(signature -> matchesSignature(fileBytes, signature));

            if (!signatureMatch) {
                throw new ImageValidationException(
                        "Invalid file signature. File is not a valid image format."
                );
            }

        } catch (IOException e) {
            throw new ImageValidationException("Failed to read file for signature validation", e);
        }
    }

    /**
     * Layer 4: Validate actual image content
     *
     * This attempts to actually decode the image to ensure it's not corrupted
     * or maliciously crafted to exploit image parsers.
     */
    private void validateImageContent(MultipartFile file) throws ImageValidationException {
        try {
            byte[] fileBytes = file.getBytes();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(fileBytes));

            if (image == null) {
                throw new ImageValidationException(
                        "File cannot be decoded as an image. Possibly corrupted or malformed."
                );
            }

        } catch (IOException e) {
            throw new ImageValidationException("Failed to decode image content", e);
        }
    }

    /**
     * Layer 5: Validate image dimensions (prevent image bombs)
     *
     * Attackers can create images with huge dimensions (e.g., 1000000x1000000)
     * that consume massive memory when decoded.
     */
    private void validateImageDimensions(MultipartFile file) throws ImageValidationException {
        try {
            byte[] fileBytes = file.getBytes();
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(fileBytes));

            if (image == null) {
                // Already handled in validateImageContent
                return;
            }

            int width = image.getWidth();
            int height = image.getHeight();

            if (width > MAX_IMAGE_WIDTH) {
                throw new ImageValidationException(
                        "Image width too large. Maximum: " + MAX_IMAGE_WIDTH + "px, Got: " + width + "px"
                );
            }

            if (height > MAX_IMAGE_HEIGHT) {
                throw new ImageValidationException(
                        "Image height too large. Maximum: " + MAX_IMAGE_HEIGHT + "px, Got: " + height + "px"
                );
            }

            // Check aspect ratio (prevent 1px x 1000000px attacks)
            double aspectRatio = (double) width / height;
            if (aspectRatio > 10 || aspectRatio < 0.1) {
                throw new ImageValidationException(
                        "Invalid aspect ratio. Image appears malformed."
                );
            }

        } catch (IOException e) {
            throw new ImageValidationException("Failed to read image dimensions", e);
        }
    }

    /**
     * Helper: Check if file bytes match a signature
     */
    private boolean matchesSignature(byte[] fileBytes, byte[] signature) {
        if (fileBytes.length < signature.length) {
            return false;
        }

        for (int i = 0; i < signature.length; i++) {
            if (fileBytes[i] != signature[i]) {
                return false;
            }
        }

        return true;
    }

    /**
     * Helper: Extract file extension
     */
    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1).toLowerCase();
    }

    /**
     * Custom exception for image validation failures
     */
    public static class ImageValidationException extends Exception {
        public ImageValidationException(String message) {
            super(message);
        }

        public ImageValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}