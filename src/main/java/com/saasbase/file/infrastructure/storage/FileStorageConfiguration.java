package com.saasbase.file;

import com.saasbase.file.application.FilePolicy;
import com.saasbase.file.infrastructure.storage.FileStorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FileStorageProperties.class)
public class FileStorageConfiguration {

    @Bean
    FilePolicy filePolicy(FileStorageProperties properties) {
        return new FilePolicy(
                properties.maxSize().toBytes(),
                properties.allowedExtensions(),
                properties.allowedContentTypes(),
                properties.inlineContentTypes(),
                properties.contentTypeByExtension());
    }
}
