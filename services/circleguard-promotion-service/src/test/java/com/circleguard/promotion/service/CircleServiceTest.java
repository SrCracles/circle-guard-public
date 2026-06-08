package com.circleguard.promotion.service;

import com.circleguard.promotion.model.graph.CircleNode;
import com.circleguard.promotion.model.graph.UserNode;
import com.circleguard.promotion.repository.graph.CircleNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CircleServiceTest {

    @Mock
    private CircleNodeRepository circleRepository;

    @Mock
    private HealthStatusService healthStatusService;

    @InjectMocks
    private CircleService circleService;

    private CircleNode circleNode;
    private UserNode userNode;

    @BeforeEach
    void setUp() {
        userNode = UserNode.builder()
                .anonymousId("user-123")
                .status("ACTIVE")
                .build();

        circleNode = CircleNode.builder()
                .id(1L)
                .name("Test Circle")
                .inviteCode("MESH-XXXX")
                .isActive(true)
                .isValid(true)
                .forceFence(false)
                .members(Set.of(userNode))
                .build();
    }

    @Test
    void toggleCircleValidity_ToInvalid() {
        when(circleRepository.findById(1L)).thenReturn(Optional.of(circleNode));
        when(circleRepository.save(any(CircleNode.class))).thenReturn(circleNode);

        circleService.toggleCircleValidity(1L);

        assertFalse(circleNode.getIsValid());
        verify(circleRepository).save(circleNode);
        verify(healthStatusService).resolveStatus("user-123");
    }

    @Test
    void toggleCircleValidity_ToValid() {
        circleNode.setIsValid(false);
        when(circleRepository.findById(1L)).thenReturn(Optional.of(circleNode));
        when(circleRepository.save(any(CircleNode.class))).thenReturn(circleNode);

        circleService.toggleCircleValidity(1L);

        assertTrue(circleNode.getIsValid());
        verify(circleRepository).save(circleNode);
        verify(healthStatusService, never()).resolveStatus(anyString());
    }

    @Test
    void forceFenceCircle_Success() {
        when(circleRepository.findById(1L)).thenReturn(Optional.of(circleNode));
        when(circleRepository.save(any(CircleNode.class))).thenReturn(circleNode);

        circleService.forceFenceCircle(1L);

        assertTrue(circleNode.getForceFence());
        verify(circleRepository).save(circleNode);
        verify(healthStatusService).updateStatus("user-123", "PROBABLE");
    }

    @Test
    void createCircle_Success() {
        when(circleRepository.existsByInviteCode(anyString())).thenReturn(false);
        when(circleRepository.save(any(CircleNode.class))).thenAnswer(i -> i.getArguments()[0]);

        CircleNode result = circleService.createCircle("New Circle", "LOC-1");

        assertNotNull(result);
        assertEquals("New Circle", result.getName());
        assertEquals("LOC-1", result.getLocationId());
        assertTrue(result.getIsActive());
        assertNotNull(result.getInviteCode());
        verify(circleRepository).save(any(CircleNode.class));
    }

    @Test
    void joinCircle_Success() {
        when(circleRepository.joinCircle("user-123", "MESH-XXXX")).thenReturn(Optional.of(circleNode));

        CircleNode result = circleService.joinCircle("user-123", "MESH-XXXX");

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void addMember_Success() {
        when(circleRepository.addUserToCircle("user-123", 1L)).thenReturn(Optional.of(circleNode));

        CircleNode result = circleService.addMember(1L, "user-123");

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void getUserCircles_ReturnsList() {
        when(circleRepository.findCirclesByUser("user-123")).thenReturn(List.of(circleNode));

        List<CircleNode> result = circleService.getUserCircles("user-123");

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
    }
}
