package com.saasbase.file.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "saasbase.file")
public record FileStorageProperties(Path rootPath) {
}
