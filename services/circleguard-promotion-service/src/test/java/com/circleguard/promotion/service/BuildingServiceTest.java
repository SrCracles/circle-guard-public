package com.circleguard.promotion.service;

import com.circleguard.promotion.model.Building;
import com.circleguard.promotion.repository.jpa.BuildingRepository;
import com.circleguard.promotion.repository.jpa.FloorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BuildingServiceTest {

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private FloorRepository floorRepository;

    @InjectMocks
    private BuildingService buildingService;

    private Building building;
    private UUID buildingId;

    @BeforeEach
    void setUp() {
        buildingId = UUID.randomUUID();
        building = Building.builder()
                .id(buildingId)
                .name("Test Building")
                .code("TB1")
                .address("123 Test St")
                .description("Test description")
                .latitude(1.0)
                .longitude(2.0)
                .build();
    }

    @Test
    void createBuilding_Success() {
        when(buildingRepository.save(any(Building.class))).thenReturn(building);

        Building result = buildingService.createBuilding("Test Building", "TB1", "Test description", 1.0, 2.0, "123 Test St");

        assertNotNull(result);
        assertEquals("Test Building", result.getName());
        assertEquals("TB1", result.getCode());
        verify(buildingRepository, times(1)).save(any(Building.class));
    }

    @Test
    void getAllBuildings_Success() {
        when(buildingRepository.findAll()).thenReturn(List.of(building));

        List<Building> results = buildingService.getAllBuildings();

        assertFalse(results.isEmpty());
        assertEquals(1, results.size());
        assertEquals("Test Building", results.get(0).getName());
    }

    @Test
    void updateBuilding_Success() {
        when(buildingRepository.findById(buildingId)).thenReturn(Optional.of(building));
        when(buildingRepository.save(any(Building.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Building result = buildingService.updateBuilding(buildingId, "New Name", "NB1", "New Desc", 3.0, 4.0, "New Addr");

        assertNotNull(result);
        assertEquals("New Name", result.getName());
        assertEquals("NB1", result.getCode());
        verify(buildingRepository, times(1)).save(building);
    }

    @Test
    void updateBuilding_NotFound_ThrowsException() {
        when(buildingRepository.findById(buildingId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> 
            buildingService.updateBuilding(buildingId, "N", "C", "D", 1.0, 1.0, "A"));
    }

    @Test
    void deleteBuilding_Success() {
        when(floorRepository.findByBuildingId(buildingId)).thenReturn(Collections.emptyList());
        doNothing().when(buildingRepository).deleteById(buildingId);

        assertDoesNotThrow(() -> buildingService.deleteBuilding(buildingId));
        verify(buildingRepository, times(1)).deleteById(buildingId);
    }

    @Test
    void deleteBuilding_HasFloors_ThrowsException() {
        when(floorRepository.findByBuildingId(buildingId)).thenReturn(List.of(mock(com.circleguard.promotion.model.Floor.class)));

        assertThrows(RuntimeException.class, () -> buildingService.deleteBuilding(buildingId));
        verify(buildingRepository, never()).deleteById(buildingId);
    }
}
