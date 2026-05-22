package com.circleguard.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

public class RoomReservationServiceTest {

    private RoomReservationServiceImpl roomReservationService;

    @BeforeEach
    void setUp() {
        roomReservationService = new RoomReservationServiceImpl();
        ReflectionTestUtils.setField(roomReservationService, "roomBookingApiUrl", "https://facilities.university.edu/api/v1/rooms");
    }

    @Test
    void testCancelReservation() {
        CompletableFuture<Void> future = roomReservationService.cancelReservation("circle-1", "loc-1");
        future.join();
        assertThat(future).isCompleted();
    }
}
