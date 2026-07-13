package com.saasbase.file.infrastructure.storage;

import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;

import java.util.Locale;
import java.util.Set;

public final class FilePolicy {

    private static final int MAX_FILENAME_LENGTH = 255;

    private final FileStorageProperties properties;

    public FilePolicy(FileStorageProperties properties) {
        this.properties = properties;
    }

    public ValidatedFile validate(String filename, String contentType, long size) {
        String sanitizedFilename = sanitizeFilename(filename);
        if (size <= 0) {
            throw new BizException(ErrorCode.FILE_INVALID);
        }
        if (size > properties.maxSize().toBytes()) {
            throw new BizException(ErrorCode.FILE_SIZE_EXCEEDED);
        }

        String extension = extensionOf(sanitizedFilename);
        String normalizedContentType = normalizeContentType(contentType);
        if (!properties.allowedExtensions().contains(extension)
                || !properties.allowedContentTypes().contains(normalizedContentType)) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        Set<String> contentTypes = properties.contentTypeByExtension().get(extension);
        if (contentTypes == null || !contentTypes.contains(normalizedContentType)) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        return new ValidatedFile(sanitizedFilename, extension, normalizedContentType);
    }

    public boolean forceAttachment(String contentType) {
        return contentType == null || !properties.inlineContentTypes().contains(contentType);
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new BizException(ErrorCode.FILE_INVALID);
        }
        int separator = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        String sanitized = filename.substring(separator + 1)
                .replaceAll("\\p{Cc}", "")
                .trim();
        if (sanitized.isEmpty() || sanitized.length() > MAX_FILENAME_LENGTH) {
            throw new BizException(ErrorCode.FILE_INVALID);
        }
        return sanitized;
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 1 || dot == filename.length() - 1) {
            throw new BizException(ErrorCode.FILE_INVALID);
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            throw new BizException(ErrorCode.FILE_TYPE_NOT_ALLOWED);
        }
        return contentType.trim();
    }
}
