package com.saasbase.file.domain;

public record StoredObject(String storageType, String objectKey, long size) {
}
