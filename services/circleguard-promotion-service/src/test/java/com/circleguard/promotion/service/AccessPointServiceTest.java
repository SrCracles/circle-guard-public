package com.circleguard.promotion.service;

import com.circleguard.promotion.model.AccessPoint;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.repository.jpa.AccessPointRepository;
import com.circleguard.promotion.repository.jpa.FloorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessPointServiceTest {

    @Mock
    private AccessPointRepository accessPointRepository;

    @Mock
    private FloorRepository floorRepository;

    @InjectMocks
    private AccessPointService accessPointService;

    private UUID floorId;
    private UUID apId;
    private Floor floor;
    private AccessPoint accessPoint;

    @BeforeEach
    void setUp() {
        floorId = UUID.randomUUID();
        apId = UUID.randomUUID();
        
        floor = new Floor();
        floor.setId(floorId);
        
        accessPoint = AccessPoint.builder()
                .floor(floor)
                .macAddress("00:11:22:33:44:55")
                .coordinateX(10.0)
                .coordinateY(20.0)
                .name("Test AP")
                .build();
        accessPoint.setId(apId);
    }

    @Test
    void registerAccessPoint_Success() {
        when(floorRepository.findById(floorId)).thenReturn(Optional.of(floor));
        when(accessPointRepository.save(any(AccessPoint.class))).thenReturn(accessPoint);

        AccessPoint result = accessPointService.registerAccessPoint(floorId, "00:11:22:33:44:55", 10.0, 20.0, "Test AP");

        assertNotNull(result);
        assertEquals("Test AP", result.getName());
        verify(floorRepository).findById(floorId);
        verify(accessPointRepository).save(any(AccessPoint.class));
    }

    @Test
    void registerAccessPoint_FloorNotFound() {
        when(floorRepository.findById(floorId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> 
            accessPointService.registerAccessPoint(floorId, "00", 1.0, 1.0, "AP")
        );
    }

    @Test
    void getAccessPoint_Found() {
        when(accessPointRepository.findById(apId)).thenReturn(Optional.of(accessPoint));
        Optional<AccessPoint> result = accessPointService.getAccessPoint(apId);
        assertTrue(result.isPresent());
        assertEquals(apId, result.get().getId());
    }

    @Test
    void getAccessPointsByFloor() {
        when(accessPointRepository.findByFloorId(floorId)).thenReturn(List.of(accessPoint));
        List<AccessPoint> result = accessPointService.getAccessPointsByFloor(floorId);
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
    }

    @Test
    void updateAccessPoint_Success() {
        when(accessPointRepository.findById(apId)).thenReturn(Optional.of(accessPoint));
        when(accessPointRepository.save(any(AccessPoint.class))).thenReturn(accessPoint);

        AccessPoint result = accessPointService.updateAccessPoint(apId, "newMac", 15.0, 25.0, "Updated AP");

        assertEquals("Updated AP", result.getName());
        assertEquals("newMac", result.getMacAddress());
        verify(accessPointRepository).save(accessPoint);
    }

    @Test
    void deleteAccessPoint_Success() {
        doNothing().when(accessPointRepository).deleteById(apId);
        accessPointService.deleteAccessPoint(apId);
        verify(accessPointRepository).deleteById(apId);
    }
}
