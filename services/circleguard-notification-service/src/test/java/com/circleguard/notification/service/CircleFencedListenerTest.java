package com.circleguard.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CircleFencedListenerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private RoomReservationService roomReservationService;

    @InjectMocks
    private CircleFencedListener listener;

    private String jsonMessage;

    @BeforeEach
    void setUp() {
        jsonMessage = "{\"circleId\":\"circle-1\",\"locationId\":\"loc-1\"}";
    }

    @Test
    void shouldCancelReservationWhenLocationIdPresent() throws Exception {
        Map<String, Object> payload = Map.of("circleId", "circle-1", "locationId", "loc-1");
        when(objectMapper.readValue(eq(jsonMessage), any(TypeReference.class))).thenReturn(payload);

        listener.handleCircleFenced(jsonMessage);

        verify(roomReservationService).cancelReservation("circle-1", "loc-1");
    }

    @Test
    void shouldSkipCancellationWhenLocationIdIsEmpty() throws Exception {
        Map<String, Object> payload = Map.of("circleId", "circle-1", "locationId", "");
        when(objectMapper.readValue(eq(jsonMessage), any(TypeReference.class))).thenReturn(payload);

        listener.handleCircleFenced(jsonMessage);

        verify(roomReservationService, never()).cancelReservation(any(), any());
    }

    @Test
    void shouldHandleParseExceptionGracefully() throws Exception {
        when(objectMapper.readValue(eq(jsonMessage), any(TypeReference.class)))
                .thenThrow(new RuntimeException("Invalid JSON"));

        listener.handleCircleFenced(jsonMessage);

        verify(roomReservationService, never()).cancelReservation(any(), any());
    }
}
