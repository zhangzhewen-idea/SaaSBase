package com.saasbase.file.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@ConfigurationProperties(prefix = "saasbase.file")
public record FileStorageProperties(
        Path rootPath,
        DataSize maxSize,
        Set<String> allowedExtensions,
        Set<String> allowedContentTypes,
        Set<String> inlineContentTypes,
        Map<String, Set<String>> contentTypeByExtension) {

    public FileStorageProperties {
        rootPath = Objects.requireNonNull(rootPath, "rootPath must not be null");
        maxSize = Objects.requireNonNull(maxSize, "maxSize must not be null");
        allowedExtensions = immutableNonEmptySet(allowedExtensions, "allowedExtensions");
        allowedContentTypes = immutableNonEmptySet(allowedContentTypes, "allowedContentTypes");
        inlineContentTypes = immutableNonEmptySet(inlineContentTypes, "inlineContentTypes");
        contentTypeByExtension = immutableNonEmptyMap(contentTypeByExtension);
    }

    private static Set<String> immutableNonEmptySet(Set<String> values, String name) {
        Objects.requireNonNull(values, name + " must not be null");
        if (values.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return Set.copyOf(values);
    }

    private static Map<String, Set<String>> immutableNonEmptyMap(Map<String, Set<String>> values) {
        Objects.requireNonNull(values, "contentTypeByExtension must not be null");
        if (values.isEmpty()) {
            throw new IllegalArgumentException("contentTypeByExtension must not be empty");
        }
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        values.forEach((extension, contentTypes) ->
                copy.put(Objects.requireNonNull(extension, "extension must not be null"),
                        immutableNonEmptySet(contentTypes, "content types for " + extension)));
        return Map.copyOf(copy);
    }
}
