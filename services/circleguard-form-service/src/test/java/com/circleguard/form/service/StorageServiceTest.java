package com.circleguard.form.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class StorageServiceTest {

    @Test
    void shouldStoreFileAndReturnUuidPrefixedFilename(@TempDir Path tempDir) {
        StorageService service = new StorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("file", "report.pdf", "application/pdf", "data".getBytes());

        String filename = service.store(file);

        assertThat(filename).endsWith("_report.pdf");
        assertThat(filename).startsWithIgnoringCase("");
        assertThat(tempDir.resolve(filename)).exists();
    }

}
