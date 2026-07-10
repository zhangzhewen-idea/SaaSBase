package com.saasbase.file.domain.gateway;

import com.saasbase.file.domain.FileObject;

import java.io.InputStream;

public interface FileStorageGateway {
    FileObject store(Long tenantId, String filename, String contentType, InputStream inputStream);

    InputStream load(Long tenantId, String objectKey);
}
