package com.saasbase.file.domain.gateway;

import com.saasbase.file.domain.StoredObject;

import java.io.InputStream;

public interface FileStorageGateway {
    StoredObject store(Long tenantId, InputStream inputStream);

    InputStream load(Long tenantId, String objectKey);

    void delete(Long tenantId, String objectKey);
}
