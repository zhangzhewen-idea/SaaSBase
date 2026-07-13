package com.saasbase.file.infrastructure.storage;

import com.saasbase.file.domain.StoredObject;
import com.saasbase.file.domain.gateway.FileStorageGateway;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

@Component
public class LocalFileStorageGateway implements FileStorageGateway {
    private final Path rootPath;

    public LocalFileStorageGateway(FileStorageProperties properties) {
        this.rootPath = properties.rootPath();
    }

    @Override
    public StoredObject store(Long tenantId, InputStream inputStream) {
        try {
            Files.createDirectories(rootPath);
            String objectKey = objectKeyOf(tenantId);
            Path target = resolveTenantPath(tenantId, objectKey);
            Files.createDirectories(target.getParent());
            long size = Files.copy(inputStream, target);
            return new StoredObject("local", objectKey, size);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to store file", ex);
        }
    }

    @Override
    public InputStream load(Long tenantId, String objectKey) {
        try {
            Path target = resolveTenantPath(tenantId, objectKey);
            return Files.newInputStream(target);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to load file", ex);
        }
    }

    @Override
    public void delete(Long tenantId, String objectKey) {
        try {
            Path target = resolveTenantPath(tenantId, objectKey);
            Files.deleteIfExists(target);
            pruneEmptyParents(target.getParent());
        } catch (IOException ex) {
            throw new IllegalStateException("failed to delete file", ex);
        }
    }

    private String objectKeyOf(Long tenantId) {
        return requireTenantId(tenantId) + "/" + UUID.randomUUID();
    }

    private Path resolveTenantPath(Long tenantId, String objectKey) {
        String normalizedObjectKey = requireObjectKey(tenantId, objectKey);
        Path target = rootPath.resolve(normalizedObjectKey).normalize();
        if (!target.startsWith(rootPath)) {
            throw new IllegalArgumentException("invalid object key");
        }
        return target;
    }

    private String requireObjectKey(Long tenantId, String objectKey) {
        String tenantPrefix = requireTenantId(tenantId) + "/";
        if (objectKey == null || !objectKey.startsWith(tenantPrefix)) {
            throw new IllegalArgumentException("object key tenant mismatch");
        }
        return objectKey;
    }

    private String requireTenantId(Long tenantId) {
        return Objects.requireNonNull(tenantId, "tenantId must not be null").toString();
    }

    private void pruneEmptyParents(Path directory) throws IOException {
        Path current = directory;
        while (current != null && !current.equals(rootPath)) {
            if (!Files.isDirectory(current)) {
                break;
            }
            try (var children = Files.list(current)) {
                if (children.findAny().isPresent()) {
                    break;
                }
            }
            Files.deleteIfExists(current);
            current = current.getParent();
        }
    }
}
