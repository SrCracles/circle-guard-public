package com.circleguard.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExposureNotificationListenerTest {

    @Mock
    private NotificationDispatcher dispatcher;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private LmsService lmsService;

    @InjectMocks
    private ExposureNotificationListener listener;

    @Test
    void shouldHandleStatusChangeEventWithoutError() throws Exception {
        String mockEvent = "{\"anonymousId\": \"user-123\", \"status\": \"EXPOSED\"}";
        JsonNode mockNode = mock(JsonNode.class);
        JsonNode mockAnonymousIdNode = mock(JsonNode.class);
        JsonNode mockStatusNode = mock(JsonNode.class);
        when(objectMapper.readTree(mockEvent)).thenReturn(mockNode);
        when(mockNode.path("anonymousId")).thenReturn(mockAnonymousIdNode);
        when(mockAnonymousIdNode.asText("unknown")).thenReturn("user-123");
        when(mockNode.path("status")).thenReturn(mockStatusNode);
        when(mockStatusNode.asText("UNKNOWN")).thenReturn("EXPOSED");

        assertDoesNotThrow(() -> listener.handleStatusChange(mockEvent));
    }
}
