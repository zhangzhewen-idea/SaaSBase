package com.saasbase.file.application;

import com.saasbase.common.error.BizException;
import com.saasbase.common.error.ErrorCode;
import com.saasbase.file.infrastructure.storage.FilePolicy;
import com.saasbase.file.infrastructure.storage.FileStorageProperties;
import com.saasbase.file.infrastructure.storage.ValidatedFile;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilePolicyTest {

    private static final long MAX_SIZE = DataSize.ofMegabytes(20).toBytes();

    private final FilePolicy policy = new FilePolicy(new FileStorageProperties(
            Path.of("./data/files"),
            DataSize.ofMegabytes(20),
            Set.of("pdf", "png", "jpg", "jpeg", "docx", "xlsx"),
            Set.of("application/pdf", "image/png", "image/jpeg",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Set.of("application/pdf", "image/png", "image/jpeg"),
            Map.of(
                    "pdf", Set.of("application/pdf"),
                    "png", Set.of("image/png"),
                    "jpg", Set.of("image/jpeg"),
                    "jpeg", Set.of("image/jpeg"),
                    "docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                    "xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))));

    @Test
    void accepts_uppercase_extension_and_normalizes_it() {
        ValidatedFile file = policy.validate("REPORT.PDF", " application/pdf ", MAX_SIZE);

        assertThat(file.filename()).isEqualTo("REPORT.PDF");
        assertThat(file.extension()).isEqualTo("pdf");
        assertThat(file.contentType()).isEqualTo("application/pdf");
    }

    @Test
    void rejects_empty_file() {
        assertError("report.pdf", "application/pdf", 0, ErrorCode.FILE_INVALID);
    }

    @Test
    void accepts_exact_size_limit() {
        assertThat(policy.validate("report.pdf", "application/pdf", MAX_SIZE).extension()).isEqualTo("pdf");
    }

    @Test
    void rejects_one_byte_over_size_limit() {
        assertError("report.pdf", "application/pdf", MAX_SIZE + 1, ErrorCode.FILE_SIZE_EXCEEDED);
    }

    @Test
    void rejects_extension_outside_allowlist() {
        assertError("report.exe", "application/pdf", 1, ErrorCode.FILE_TYPE_NOT_ALLOWED);
    }

    @Test
    void rejects_content_type_outside_allowlist() {
        assertError("report.pdf", "text/html", 1, ErrorCode.FILE_TYPE_NOT_ALLOWED);
    }

    @Test
    void rejects_invalid_extension_and_content_type_pair() {
        assertError("report.pdf", "image/png", 1, ErrorCode.FILE_TYPE_NOT_ALLOWED);
    }

    @Test
    void keeps_only_last_path_segment() {
        assertThat(policy.validate("../private\\reports/report.pdf", "application/pdf", 1).filename())
                .isEqualTo("report.pdf");
    }

    @Test
    void removes_control_characters() {
        assertThat(policy.validate("re\u0000po\nrt.pdf", "application/pdf", 1).filename())
                .isEqualTo("report.pdf");
    }

    @Test
    void rejects_filename_longer_than_255_characters() {
        assertError("a".repeat(252) + ".pdf", "application/pdf", 1, ErrorCode.FILE_INVALID);
    }

    @Test
    void forces_html_and_null_content_types_to_download() {
        assertThat(policy.forceAttachment("text/html")).isTrue();
        assertThat(policy.forceAttachment(null)).isTrue();
        assertThat(policy.forceAttachment("application/pdf")).isFalse();
    }

    private void assertError(String filename, String contentType, long size, ErrorCode errorCode) {
        assertThatThrownBy(() -> policy.validate(filename, contentType, size))
                .isInstanceOfSatisfying(BizException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(errorCode));
    }
}
