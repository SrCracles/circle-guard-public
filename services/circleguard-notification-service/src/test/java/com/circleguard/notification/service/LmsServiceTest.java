package com.circleguard.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

public class LmsServiceTest {

    private LmsServiceImpl lmsService;

    @BeforeEach
    void setUp() {
        lmsService = new LmsServiceImpl();
        ReflectionTestUtils.setField(lmsService, "lmsApiUrl", "https://lms.university.edu/api/v1");
        ReflectionTestUtils.setField(lmsService, "identityApiUrl", "http://circleguard-identity-service:8081");
    }

    @Test
    void testRemoteAttendanceSync() {
        CompletableFuture<Void> future = lmsService.syncRemoteAttendance("student-123", "PROBABLE");
        future.join();
        assertThat(future).isCompleted();
    }
}
