package com.saasbase.file.adapter;

import com.saasbase.common.api.ApiResponse;
import com.saasbase.common.api.PageResponse;
import com.saasbase.file.application.FileApplicationService;
import com.saasbase.file.domain.FileMetadata;
import jakarta.validation.constraints.Pattern;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Validated
@RestController
@RequestMapping("/api/v1/admin/files")
public class FileController {

    private final FileApplicationService fileApplicationService;

    public FileController(FileApplicationService fileApplicationService) {
        this.fileApplicationService = fileApplicationService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('tenant:file:write')")
    public ResponseEntity<ApiResponse<FileView>> upload(@RequestPart("file") MultipartFile file) throws IOException {
        FileMetadata metadata = fileApplicationService.upload(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                file.getInputStream());
        return ResponseEntity.ok(ApiResponse.ok(FileView.from(metadata)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('tenant:file:read')")
    public ResponseEntity<ApiResponse<FileView>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(FileView.from(fileApplicationService.get(id))));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('tenant:file:read')")
    public ResponseEntity<ApiResponse<PageResponse<FileView>>> list(@ModelAttribute FileListQuery query) {
        PageResponse<FileMetadata> page = fileApplicationService.search(query.toFileQuery());
        PageResponse<FileView> response = new PageResponse<>(
                page.items().stream().map(FileView::from).toList(),
                page.total(),
                page.pageNo(),
                page.pageSize());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}/content")
    @PreAuthorize("hasAuthority('tenant:file:read')")
    public ResponseEntity<InputStreamResource> content(
            @PathVariable Long id,
            @RequestParam(defaultValue = "attachment") @Pattern(regexp = "inline|attachment") String disposition) {
        FileMetadata metadata = fileApplicationService.get(id);
        boolean inline = "inline".equals(disposition) && isInline(metadata.contentType());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(metadata.contentType()));
        headers.setContentDisposition(ContentDisposition.builder(inline ? "inline" : "attachment")
                .filename(metadata.originalFilename(), StandardCharsets.UTF_8)
                .build());
        headers.add(HttpHeaders.CONTENT_LENGTH, String.valueOf(metadata.size()));
        headers.add("X-Content-Type-Options", "nosniff");
        InputStream inputStream = fileApplicationService.openContent(id);
        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(inputStream));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('tenant:file:delete')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        fileApplicationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private boolean isInline(String contentType) {
        return "application/pdf".equals(contentType)
                || "image/png".equals(contentType)
                || "image/jpeg".equals(contentType);
    }
}
