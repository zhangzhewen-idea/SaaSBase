package com.saasbase.file.infrastructure.storage;

import com.saasbase.file.domain.FileObject;
import com.saasbase.file.domain.gateway.FileStorageGateway;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
public class LocalFileStorageGateway implements FileStorageGateway {
    private final Path rootPath;

    public LocalFileStorageGateway(FileStorageProperties properties) {
        this.rootPath = properties.rootPath();
    }

    @Override
    public FileObject store(Long tenantId, String filename, String contentType, InputStream inputStream) {
        try {
            Files.createDirectories(rootPath);
            String objectKey = tenantId + "/" + UUID.randomUUID();
            Path target = rootPath.resolve(objectKey).normalize();
            if (!target.startsWith(rootPath)) {
                throw new IllegalArgumentException("invalid object key");
            }
            Files.createDirectories(target.getParent());
            long size = Files.copy(inputStream, target);
            return new FileObject(tenantId, "local", objectKey, filename, contentType, size);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to store file", ex);
        }
    }

    @Override
    public InputStream load(Long tenantId, String objectKey) {
        try {
            Path target = rootPath.resolve(objectKey).normalize();
            if (!target.startsWith(rootPath)) {
                throw new IllegalArgumentException("invalid object key");
            }
            return Files.newInputStream(target);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to load file", ex);
        }
    }
}
