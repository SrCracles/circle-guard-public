package com.circleguard.promotion.controller;

import com.circleguard.promotion.dto.AccessPointDTO;
import com.circleguard.promotion.dto.BuildingDTO;
import com.circleguard.promotion.dto.FloorDTO;
import com.circleguard.promotion.model.AccessPoint;
import com.circleguard.promotion.model.Building;
import com.circleguard.promotion.model.Floor;
import com.circleguard.promotion.service.AccessPointService;
import com.circleguard.promotion.service.BuildingService;
import com.circleguard.promotion.service.FloorService;
import com.circleguard.promotion.service.LocationResolutionService;
import com.circleguard.promotion.service.MacSessionRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {
        BuildingController.class,
        AccessPointController.class,
        FloorController.class,
        LocationSignalController.class,
        SessionHandshakeController.class
})
@AutoConfigureMockMvc(addFilters = false)
public class PromotionControllersTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BuildingService buildingService;

    @MockBean
    private FloorService floorService;

    @MockBean
    private AccessPointService accessPointService;

    @MockBean
    private LocationResolutionService locationResolutionService;

    @MockBean
    private MacSessionRegistry sessionRegistry;

    @Test
    void buildingControllerCreateAndList() throws Exception {
        UUID id = UUID.randomUUID();
        Building building = Building.builder().id(id).name("Bldg").code("B1").build();
        when(buildingService.createBuilding(any(), any(), any(), any(), any(), any())).thenReturn(building);
        when(buildingService.getAllBuildings()).thenReturn(List.of(building));

        BuildingDTO request = BuildingDTO.builder().name("Bldg").code("B1").build();

        mockMvc.perform(post("/api/v1/buildings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bldg"));

        mockMvc.perform(get("/api/v1/buildings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("B1"));
    }

    @Test
    void buildingControllerUpdateAndDelete() throws Exception {
        UUID id = UUID.randomUUID();
        Building building = Building.builder().id(id).name("Upd").code("B1").build();
        when(buildingService.updateBuilding(any(), any(), any(), any(), any(), any(), any())).thenReturn(building);
        doNothing().when(buildingService).deleteBuilding(id);

        BuildingDTO request = BuildingDTO.builder().name("Upd").code("B1").build();

        mockMvc.perform(put("/api/v1/buildings/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Upd"));

        mockMvc.perform(delete("/api/v1/buildings/{id}", id))
                .andExpect(status().isOk());
    }

    @Test
    void buildingControllerGetFloorsAndAddFloor() throws Exception {
        UUID buildingId = UUID.randomUUID();
        Floor floor = Floor.builder().id(UUID.randomUUID()).floorNumber(1).name("F1").build();
        when(floorService.getFloorsByBuilding(buildingId)).thenReturn(List.of(floor));
        when(floorService.addFloor(eq(buildingId), any(), any())).thenReturn(floor);

        mockMvc.perform(get("/api/v1/buildings/{id}/floors", buildingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("F1"));

        FloorDTO request = FloorDTO.builder().floorNumber(1).name("F1").build();
        mockMvc.perform(post("/api/v1/buildings/{id}/floors", buildingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("F1"));
    }

    @Test
    void accessPointControllerGetAndUpdate() throws Exception {
        UUID id = UUID.randomUUID();
        AccessPoint ap = AccessPoint.builder().id(id).macAddress("00:11").name("AP").build();
        when(accessPointService.getAccessPoint(id)).thenReturn(Optional.of(ap));
        when(accessPointService.updateAccessPoint(any(), any(), any(), any(), any())).thenReturn(ap);

        mockMvc.perform(get("/api/v1/access-points/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("AP"));

        AccessPointDTO request = AccessPointDTO.builder().macAddress("00:11").name("AP").build();
        mockMvc.perform(put("/api/v1/access-points/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("AP"));
    }

    @Test
    void accessPointControllerNotFoundAndDelete() throws Exception {
        UUID id = UUID.randomUUID();
        when(accessPointService.getAccessPoint(id)).thenReturn(Optional.empty());
        doNothing().when(accessPointService).deleteAccessPoint(id);

        mockMvc.perform(get("/api/v1/access-points/{id}", id))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/access-points/{id}", id))
                .andExpect(status().isOk());
    }

    @Test
    void floorControllerAddAndGetAccessPoints() throws Exception {
        UUID floorId = UUID.randomUUID();
        AccessPoint ap = AccessPoint.builder().id(UUID.randomUUID()).macAddress("00:11").name("AP").build();
        when(accessPointService.registerAccessPoint(eq(floorId), any(), any(), any(), any())).thenReturn(ap);
        when(accessPointService.getAccessPointsByFloor(floorId)).thenReturn(List.of(ap));

        AccessPointDTO request = AccessPointDTO.builder().macAddress("00:11").name("AP").build();
        mockMvc.perform(post("/api/v1/floors/{id}/access-points", floorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("AP"));

        mockMvc.perform(get("/api/v1/floors/{id}/access-points", floorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("AP"));
    }

    @Test
    void floorControllerUpdateAndDelete() throws Exception {
        UUID id = UUID.randomUUID();
        Floor floor = Floor.builder().id(id).floorNumber(2).name("F2").build();
        when(floorService.updateFloor(any(), any(), any(), any())).thenReturn(floor);
        doNothing().when(floorService).deleteFloor(id);

        FloorDTO request = FloorDTO.builder().floorNumber(2).name("F2").build();
        mockMvc.perform(put("/api/v1/floors/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("F2"));

        mockMvc.perform(delete("/api/v1/floors/{id}", id))
                .andExpect(status().isOk());
    }

    @Test
    void locationSignalControllerReceiveSignal() throws Exception {
        doNothing().when(locationResolutionService).processSignal(any(), any(), any());

        mockMvc.perform(post("/api/v1/location/signal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apMac\":\"00:11\",\"deviceMac\":\"22:33\",\"rssi\":-50.0}"))
                .andExpect(status().isOk());
    }

    @Test
    void sessionHandshakeControllerHandshakeAndClose() throws Exception {
        doNothing().when(sessionRegistry).registerSession(any(), any());
        doNothing().when(sessionRegistry).closeSession(any());

        mockMvc.perform(post("/api/v1/sessions/handshake")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"macAddress\":\"00:11\",\"anonymousId\":\"anon1\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/sessions/handshake")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/sessions/{macAddress}", "00:11"))
                .andExpect(status().isNoContent());
    }
}
