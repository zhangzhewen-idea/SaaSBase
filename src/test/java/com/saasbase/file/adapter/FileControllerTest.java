package com.saasbase.file.adapter;

import com.saasbase.common.api.PageResponse;
import com.saasbase.file.application.FileApplicationService;
import com.saasbase.file.domain.FileMetadata;
import com.saasbase.file.domain.FileStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.ResourceHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    @Mock
    FileApplicationService fileApplicationService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FileController(fileApplicationService))
                .setControllerAdvice(new com.saasbase.common.error.GlobalExceptionHandler())
                .setMessageConverters(
                        new MappingJackson2HttpMessageConverter(),
                        new ByteArrayHttpMessageConverter(),
                        new StringHttpMessageConverter(),
                        new ResourceHttpMessageConverter())
                .build();
    }

    @Test
    void upload_returns_view_without_storage_details() throws Exception {
        when(fileApplicationService.upload(any(), any(), anyLong(), any()))
                .thenReturn(sampleMetadata());

        mockMvc.perform(multipart("/api/v1/admin/files")
                        .file(new MockMultipartFile("file", "report.pdf", "application/pdf",
                                "hello".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.objectKey").doesNotExist())
                .andExpect(jsonPath("$.data.originalFilename").value("report.pdf"));
    }

    @Test
    void list_maps_page_response() throws Exception {
        when(fileApplicationService.search(any()))
                .thenReturn(new PageResponse<>(List.of(sampleMetadata()), 1, 1, 10));

        mockMvc.perform(get("/api/v1/admin/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].objectKey").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].id").value(7));
    }

    @Test
    void content_sets_download_headers() throws Exception {
        when(fileApplicationService.get(7L)).thenReturn(sampleMetadata());
        when(fileApplicationService.openContent(7L)).thenReturn(new ByteArrayInputStream("hello".getBytes()));

        mockMvc.perform(get("/api/v1/admin/files/7/content").param("disposition", "inline"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("inline")))
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    void delete_returns_no_content() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/files/7"))
                .andExpect(status().isNoContent());
    }

    private FileMetadata sampleMetadata() {
        return new FileMetadata(
                7L, 2001L, "local", "2001/object", "report.pdf", "application/pdf", "pdf", 5L,
                FileStatus.AVAILABLE, Instant.parse("2026-07-13T08:00:00Z"), 3001L, 1L);
    }
}
