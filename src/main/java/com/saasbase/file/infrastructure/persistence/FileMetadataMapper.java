package com.saasbase.file.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Mapper
public interface FileMetadataMapper {

    @InterceptorIgnore(tenantLine = "1")
    int insertUploading(@Param("metadata") FileMetadataRecord metadata);

    @InterceptorIgnore(tenantLine = "1")
    int markAvailable(@Param("tenantId") Long tenantId,
                      @Param("id") Long id,
                      @Param("storageType") String storageType,
                      @Param("objectKey") String objectKey,
                      @Param("size") long size,
                      @Param("version") long version,
                      @Param("updatedAt") Instant updatedAt,
                      @Param("updatedBy") Long updatedBy);

    @InterceptorIgnore(tenantLine = "1")
    int markDeleteFailed(@Param("tenantId") Long tenantId,
                         @Param("id") Long id,
                         @Param("version") long version,
                         @Param("updatedAt") Instant updatedAt,
                         @Param("updatedBy") Long updatedBy);

    @InterceptorIgnore(tenantLine = "1")
    Optional<FileMetadataRecord> findAvailableById(@Param("tenantId") Long tenantId,
                                                   @Param("id") Long id);

    @InterceptorIgnore(tenantLine = "1")
    Optional<FileMetadataRecord> findDeletableById(@Param("tenantId") Long tenantId,
                                                   @Param("id") Long id);

    @InterceptorIgnore(tenantLine = "1")
    List<FileMetadataRecord> search(@Param("tenantId") Long tenantId,
                                    @Param("filename") String filename,
                                    @Param("contentType") String contentType,
                                    @Param("uploadedFrom") Instant uploadedFrom,
                                    @Param("uploadedTo") Instant uploadedTo,
                                    @Param("offset") long offset,
                                    @Param("limit") long limit);

    @InterceptorIgnore(tenantLine = "1")
    long countSearch(@Param("tenantId") Long tenantId,
                     @Param("filename") String filename,
                     @Param("contentType") String contentType,
                     @Param("uploadedFrom") Instant uploadedFrom,
                     @Param("uploadedTo") Instant uploadedTo);

    @InterceptorIgnore(tenantLine = "1")
    int logicallyDelete(@Param("tenantId") Long tenantId,
                        @Param("id") Long id,
                        @Param("deletedAt") Instant deletedAt,
                        @Param("deletedBy") Long deletedBy,
                        @Param("version") long version);

    @InterceptorIgnore(tenantLine = "1")
    int removeUploading(@Param("tenantId") Long tenantId,
                        @Param("id") Long id);
}
