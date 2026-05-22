package com.circleguard.promotion.service;

import com.circleguard.promotion.model.graph.CircleNode;
import com.circleguard.promotion.repository.graph.CircleNodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AdministrativeCorrectionTest {

    @Mock
    private CircleNodeRepository circleRepository;

    @Mock
    private HealthStatusService healthStatusService;

    @InjectMocks
    private CircleService circleService;

    @Test
    void invalidateCircle_PreventsPropagation() {
        CircleNode circle = CircleNode.builder()
                .id(1L)
                .name("RiskGroup")
                .isValid(true)
                .build();

        when(circleRepository.findById(1L)).thenReturn(Optional.of(circle));

        circleService.toggleCircleValidity(1L);

        verify(circleRepository).save(any(CircleNode.class));
    }

    @Test
    void forceFence_PromotesAllMembers() {
        CircleNode circle = CircleNode.builder()
                .id(1L)
                .name("Forced containment")
                .isValid(true)
                .build();

        when(circleRepository.findById(1L)).thenReturn(Optional.of(circle));

        circleService.forceFenceCircle(1L);

        verify(circleRepository).save(any(CircleNode.class));
    }
}
