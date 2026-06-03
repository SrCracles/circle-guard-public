package com.circleguard.file.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageServiceTest {

    private FileStorageService fileStorageService;
    private final Path rootPath = Paths.get("uploads");

    @BeforeEach
    void setUp() throws IOException {
        fileStorageService = new FileStorageService();
        if (!Files.exists(rootPath)) {
            Files.createDirectories(rootPath);
        }
    }

    @AfterEach
    void tearDown() throws IOException {
        FileSystemUtils.deleteRecursively(rootPath);
    }

    @Test
    void testSaveFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "hello.txt",
                "text/plain",
                "Hello World!".getBytes()
        );

        String generatedFilename = fileStorageService.saveFile(file);

        assertNotNull(generatedFilename);
        assertTrue(generatedFilename.endsWith("_hello.txt"));
        assertTrue(Files.exists(rootPath.resolve(generatedFilename)));
    }
}
