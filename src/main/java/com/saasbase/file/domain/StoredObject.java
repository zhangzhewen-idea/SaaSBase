package com.saasbase.file.domain;

public record StoredObject(String storageType, String objectKey, long size) {
    public StoredObject {
        if (storageType == null || storageType.isBlank()) {
            throw new IllegalArgumentException("storageType must not be blank");
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("objectKey must not be blank");
        }
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
    }
}
