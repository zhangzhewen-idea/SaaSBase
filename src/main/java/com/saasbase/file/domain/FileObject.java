package com.saasbase.file.domain;

public record FileObject(Long tenantId, String storageType, String objectKey, String filename, String contentType, long size) {
}
