package com.circleguard.notification.service;

import freemarker.template.Configuration;
import freemarker.template.Template;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TemplateServiceTest {

    @Mock
    private Configuration freemarkerConfig;

    @InjectMocks
    private TemplateService templateService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(templateService, "testingUrl", "https://circleguard.example.com/testing");
        ReflectionTestUtils.setField(templateService, "isolationUrl", "https://circleguard.example.com/isolation");
        ReflectionTestUtils.setField(templateService, "guidelinesDeepLink", "circleguard://guidelines");
    }

    @Test
    void testEmailTemplateGeneration() throws Exception {
        when(freemarkerConfig.getTemplate("health_alert.ftl"))
            .thenThrow(new RuntimeException("Template not found"));

        String content = templateService.generateEmailContent("SUSPECT", "John Doe");
        assertThat(content).contains("SUSPECT");
    }

    @Test
    void testPushTemplateGeneration() {
        String content = templateService.generatePushContent("PROBABLE");
        assertThat(content).contains("Monitor symptoms");
    }

    @Test
    void testPushMetadataGeneration() {
        var metadata = templateService.generatePushMetadata("SUSPECT");
        assertThat(metadata).containsEntry("url", "circleguard://guidelines");
        
        var emptyMetadata = templateService.generatePushMetadata("OTHER");
        assertThat(emptyMetadata).isEmpty();
    }

    @Test
    void testSmsTemplateGeneration() {
        String content = templateService.generateSmsContent("SUSPECT");
        assertThat(content).contains("SUSPECT");
        assertThat(content).contains("check your email");
    }
}
