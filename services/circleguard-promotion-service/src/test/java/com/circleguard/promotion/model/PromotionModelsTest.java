package com.circleguard.promotion.model;

import com.circleguard.promotion.dto.AccessPointDTO;
import com.circleguard.promotion.dto.BuildingDTO;
import com.circleguard.promotion.dto.FloorDTO;
import com.circleguard.promotion.model.graph.CircleNode;
import com.circleguard.promotion.model.graph.EncounterRelationship;
import com.circleguard.promotion.model.graph.UserNode;
import com.circleguard.promotion.model.jpa.SystemSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class PromotionModelsTest {

    // DTOs
    @Test
    void buildingDtoBuilderAndAccessors() {
        UUID id = UUID.randomUUID();
        BuildingDTO dto = BuildingDTO.builder()
                .id(id)
                .name("Bldg")
                .code("B1")
                .description("Desc")
                .latitude(1.0)
                .longitude(2.0)
                .address("Addr")
                .floors(List.of())
                .build();

        assertThat(dto.getId()).isEqualTo(id);
        assertThat(dto.getName()).isEqualTo("Bldg");
        assertThat(dto.getCode()).isEqualTo("B1");
        assertThat(dto.getDescription()).isEqualTo("Desc");
        assertThat(dto.getLatitude()).isEqualTo(1.0);
        assertThat(dto.getLongitude()).isEqualTo(2.0);
        assertThat(dto.getAddress()).isEqualTo("Addr");
        assertThat(dto.getFloors()).isEmpty();
        assertThat(dto.toString()).contains("Bldg");
    }

    @Test
    void buildingDtoSettersAndAccessors() {
        BuildingDTO dto = new BuildingDTO();
        UUID id = UUID.randomUUID();
        dto.setId(id);
        dto.setName("N");
        dto.setCode("C");
        dto.setDescription("D");
        dto.setLatitude(3.0);
        dto.setLongitude(4.0);
        dto.setAddress("A");
        dto.setFloors(List.of());

        assertThat(dto.getId()).isEqualTo(id);
        assertThat(dto.getName()).isEqualTo("N");
        assertThat(dto.getCode()).isEqualTo("C");
        assertThat(dto.getDescription()).isEqualTo("D");
        assertThat(dto.getLatitude()).isEqualTo(3.0);
        assertThat(dto.getLongitude()).isEqualTo(4.0);
        assertThat(dto.getAddress()).isEqualTo("A");
        assertThat(dto.getFloors()).isEmpty();
    }

    @Test
    void buildingDtoEqualsAndHashCode() {
        BuildingDTO d1 = BuildingDTO.builder().name("a").build();
        BuildingDTO d2 = BuildingDTO.builder().name("a").build();
        BuildingDTO d3 = BuildingDTO.builder().name("b").build();

        assertThat(d1).isEqualTo(d2);
        assertThat(d1.hashCode()).isEqualTo(d2.hashCode());
        assertThat(d1).isNotEqualTo(d3);
        assertThat(d1).isNotEqualTo(null);
        assertThat(d1).isNotEqualTo("string");
    }

    @Test
    void accessPointDtoBuilderAndAccessors() {
        UUID id = UUID.randomUUID();
        AccessPointDTO dto = AccessPointDTO.builder()
                .id(id)
                .macAddress("00:11:22:33:44:55")
                .floorId(UUID.randomUUID())
                .coordinateX(1.0)
                .coordinateY(2.0)
                .name("AP1")
                .build();

        assertThat(dto.getId()).isEqualTo(id);
        assertThat(dto.getMacAddress()).isEqualTo("00:11:22:33:44:55");
        assertThat(dto.getCoordinateX()).isEqualTo(1.0);
        assertThat(dto.getCoordinateY()).isEqualTo(2.0);
        assertThat(dto.getName()).isEqualTo("AP1");
        assertThat(dto.toString()).contains("AP1");
    }

    @Test
    void accessPointDtoSettersAndAccessors() {
        AccessPointDTO dto = new AccessPointDTO();
        UUID id = UUID.randomUUID();
        dto.setId(id);
        dto.setMacAddress("mac");
        dto.setFloorId(UUID.randomUUID());
        dto.setCoordinateX(5.0);
        dto.setCoordinateY(6.0);
        dto.setName("N");

        assertThat(dto.getId()).isEqualTo(id);
        assertThat(dto.getMacAddress()).isEqualTo("mac");
        assertThat(dto.getCoordinateX()).isEqualTo(5.0);
        assertThat(dto.getCoordinateY()).isEqualTo(6.0);
        assertThat(dto.getName()).isEqualTo("N");
    }

    @Test
    void accessPointDtoEqualsAndHashCode() {
        AccessPointDTO d1 = AccessPointDTO.builder().name("a").build();
        AccessPointDTO d2 = AccessPointDTO.builder().name("a").build();
        AccessPointDTO d3 = AccessPointDTO.builder().name("b").build();

        assertThat(d1).isEqualTo(d2);
        assertThat(d1.hashCode()).isEqualTo(d2.hashCode());
        assertThat(d1).isNotEqualTo(d3);
        assertThat(d1).isNotEqualTo(null);
    }

    @Test
    void floorDtoBuilderAndAccessors() {
        UUID id = UUID.randomUUID();
        FloorDTO dto = FloorDTO.builder()
                .id(id)
                .buildingId(UUID.randomUUID())
                .floorNumber(1)
                .name("Floor 1")
                .floorPlanUrl("url")
                .accessPoints(List.of())
                .build();

        assertThat(dto.getId()).isEqualTo(id);
        assertThat(dto.getFloorNumber()).isEqualTo(1);
        assertThat(dto.getName()).isEqualTo("Floor 1");
        assertThat(dto.getFloorPlanUrl()).isEqualTo("url");
        assertThat(dto.getAccessPoints()).isEmpty();
        assertThat(dto.toString()).contains("Floor 1");
    }

    @Test
    void floorDtoSettersAndAccessors() {
        FloorDTO dto = new FloorDTO();
        dto.setId(UUID.randomUUID());
        dto.setBuildingId(UUID.randomUUID());
        dto.setFloorNumber(2);
        dto.setName("N");
        dto.setFloorPlanUrl("U");
        dto.setAccessPoints(List.of());

        assertThat(dto.getFloorNumber()).isEqualTo(2);
        assertThat(dto.getName()).isEqualTo("N");
        assertThat(dto.getFloorPlanUrl()).isEqualTo("U");
    }

    @Test
    void floorDtoEqualsAndHashCode() {
        FloorDTO d1 = FloorDTO.builder().name("a").build();
        FloorDTO d2 = FloorDTO.builder().name("a").build();
        FloorDTO d3 = FloorDTO.builder().name("b").build();

        assertThat(d1).isEqualTo(d2);
        assertThat(d1.hashCode()).isEqualTo(d2.hashCode());
        assertThat(d1).isNotEqualTo(d3);
        assertThat(d1).isNotEqualTo(null);
    }

    // JPA Models
    @Test
    void buildingBuilderAndAccessors() {
        UUID id = UUID.randomUUID();
        Building b = Building.builder()
                .id(id)
                .name("Bldg")
                .code("B1")
                .description("Desc")
                .latitude(1.0)
                .longitude(2.0)
                .address("Addr")
                .floors(List.of())
                .build();

        assertThat(b.getId()).isEqualTo(id);
        assertThat(b.getName()).isEqualTo("Bldg");
        assertThat(b.getCode()).isEqualTo("B1");
        assertThat(b.getDescription()).isEqualTo("Desc");
        assertThat(b.getLatitude()).isEqualTo(1.0);
        assertThat(b.getLongitude()).isEqualTo(2.0);
        assertThat(b.getAddress()).isEqualTo("Addr");
        assertThat(b.getFloors()).isEmpty();
        assertThat(b.toString()).contains("Bldg");
    }

    @Test
    void buildingSettersAndAccessors() {
        Building b = new Building();
        UUID id = UUID.randomUUID();
        b.setId(id);
        b.setName("N");
        b.setCode("C");
        b.setDescription("D");
        b.setLatitude(3.0);
        b.setLongitude(4.0);
        b.setAddress("A");
        b.setFloors(List.of());

        assertThat(b.getId()).isEqualTo(id);
        assertThat(b.getName()).isEqualTo("N");
        assertThat(b.getCode()).isEqualTo("C");
        assertThat(b.getDescription()).isEqualTo("D");
        assertThat(b.getLatitude()).isEqualTo(3.0);
        assertThat(b.getLongitude()).isEqualTo(4.0);
        assertThat(b.getAddress()).isEqualTo("A");
        assertThat(b.getFloors()).isEmpty();
    }

    @Test
    void buildingEqualsAndHashCode() {
        Building b1 = Building.builder().name("a").code("c").build();
        Building b2 = Building.builder().name("a").code("c").build();
        Building b3 = Building.builder().name("b").code("c").build();

        assertThat(b1).isEqualTo(b2);
        assertThat(b1.hashCode()).isEqualTo(b2.hashCode());
        assertThat(b1).isNotEqualTo(b3);
        assertThat(b1).isNotEqualTo(null);
        assertThat(b1).isNotEqualTo("string");
    }

    @Test
    void accessPointBuilderAndAccessors() {
        UUID id = UUID.randomUUID();
        AccessPoint ap = AccessPoint.builder()
                .id(id)
                .macAddress("00:11:22:33:44:55")
                .coordinateX(1.0)
                .coordinateY(2.0)
                .name("AP1")
                .build();

        assertThat(ap.getId()).isEqualTo(id);
        assertThat(ap.getMacAddress()).isEqualTo("00:11:22:33:44:55");
        assertThat(ap.getCoordinateX()).isEqualTo(1.0);
        assertThat(ap.getCoordinateY()).isEqualTo(2.0);
        assertThat(ap.getName()).isEqualTo("AP1");
        assertThat(ap.toString()).contains("AP1");
    }

    @Test
    void accessPointSettersAndAccessors() {
        AccessPoint ap = new AccessPoint();
        UUID id = UUID.randomUUID();
        ap.setId(id);
        ap.setMacAddress("mac");
        ap.setCoordinateX(5.0);
        ap.setCoordinateY(6.0);
        ap.setName("N");

        assertThat(ap.getId()).isEqualTo(id);
        assertThat(ap.getMacAddress()).isEqualTo("mac");
        assertThat(ap.getCoordinateX()).isEqualTo(5.0);
        assertThat(ap.getCoordinateY()).isEqualTo(6.0);
        assertThat(ap.getName()).isEqualTo("N");
    }

    @Test
    void accessPointEqualsAndHashCode() {
        AccessPoint a1 = AccessPoint.builder().macAddress("a").build();
        AccessPoint a2 = AccessPoint.builder().macAddress("a").build();
        AccessPoint a3 = AccessPoint.builder().macAddress("b").build();

        assertThat(a1).isEqualTo(a2);
        assertThat(a1.hashCode()).isEqualTo(a2.hashCode());
        assertThat(a1).isNotEqualTo(a3);
        assertThat(a1).isNotEqualTo(null);
    }

    @Test
    void floorBuilderAndAccessors() {
        UUID id = UUID.randomUUID();
        Floor f = Floor.builder()
                .id(id)
                .floorNumber(1)
                .name("Floor 1")
                .floorPlanUrl("url")
                .accessPoints(List.of())
                .build();

        assertThat(f.getId()).isEqualTo(id);
        assertThat(f.getFloorNumber()).isEqualTo(1);
        assertThat(f.getName()).isEqualTo("Floor 1");
        assertThat(f.getFloorPlanUrl()).isEqualTo("url");
        assertThat(f.getAccessPoints()).isEmpty();
        assertThat(f.toString()).contains("Floor 1");
    }

    @Test
    void floorSettersAndAccessors() {
        Floor f = new Floor();
        f.setId(UUID.randomUUID());
        f.setFloorNumber(2);
        f.setName("N");
        f.setFloorPlanUrl("U");
        f.setAccessPoints(List.of());

        assertThat(f.getFloorNumber()).isEqualTo(2);
        assertThat(f.getName()).isEqualTo("N");
        assertThat(f.getFloorPlanUrl()).isEqualTo("U");
        assertThat(f.getAccessPoints()).isEmpty();
    }

    @Test
    void floorEqualsAndHashCode() {
        Floor f1 = Floor.builder().name("a").floorNumber(1).build();
        Floor f2 = Floor.builder().name("a").floorNumber(1).build();
        Floor f3 = Floor.builder().name("b").floorNumber(1).build();

        assertThat(f1).isEqualTo(f2);
        assertThat(f1.hashCode()).isEqualTo(f2.hashCode());
        assertThat(f1).isNotEqualTo(f3);
        assertThat(f1).isNotEqualTo(null);
        assertThat(f1).isNotEqualTo("string");
    }

    @Test
    void systemSettingsBuilderAndAccessors() {
        SystemSettings s = SystemSettings.builder()
                .id(1L)
                .unconfirmedFencingEnabled(true)
                .autoThresholdSeconds(300L)
                .mandatoryFenceDays(14)
                .encounterWindowDays(2)
                .build();

        assertThat(s.getId()).isEqualTo(1L);
        assertThat(s.getUnconfirmedFencingEnabled()).isTrue();
        assertThat(s.getAutoThresholdSeconds()).isEqualTo(300L);
        assertThat(s.getMandatoryFenceDays()).isEqualTo(14);
        assertThat(s.getEncounterWindowDays()).isEqualTo(2);
        assertThat(s.toString()).contains("300");
    }

    @Test
    void systemSettingsSettersAndAccessors() {
        SystemSettings s = new SystemSettings();
        s.setId(2L);
        s.setUnconfirmedFencingEnabled(false);
        s.setAutoThresholdSeconds(600L);
        s.setMandatoryFenceDays(7);
        s.setEncounterWindowDays(3);

        assertThat(s.getId()).isEqualTo(2L);
        assertThat(s.getUnconfirmedFencingEnabled()).isFalse();
        assertThat(s.getAutoThresholdSeconds()).isEqualTo(600L);
        assertThat(s.getMandatoryFenceDays()).isEqualTo(7);
        assertThat(s.getEncounterWindowDays()).isEqualTo(3);
    }

    @Test
    void systemSettingsEqualsAndHashCode() {
        SystemSettings s1 = SystemSettings.builder().mandatoryFenceDays(1).build();
        SystemSettings s2 = SystemSettings.builder().mandatoryFenceDays(1).build();
        SystemSettings s3 = SystemSettings.builder().mandatoryFenceDays(2).build();

        assertThat(s1).isEqualTo(s2);
        assertThat(s1.hashCode()).isEqualTo(s2.hashCode());
        assertThat(s1).isNotEqualTo(s3);
        assertThat(s1).isNotEqualTo(null);
    }

    // Graph Models
    @Test
    void circleNodeBuilderAndAccessors() {
        CircleNode node = CircleNode.builder()
                .id(1L)
                .name("Circle")
                .inviteCode("ABC")
                .createdAt(123456L)
                .locationId("loc1")
                .isValid(true)
                .forceFence(false)
                .isActive(true)
                .isAutoGenerated(false)
                .members(Set.of())
                .build();

        assertThat(node.getId()).isEqualTo(1L);
        assertThat(node.getName()).isEqualTo("Circle");
        assertThat(node.getInviteCode()).isEqualTo("ABC");
        assertThat(node.getCreatedAt()).isEqualTo(123456L);
        assertThat(node.getLocationId()).isEqualTo("loc1");
        assertThat(node.getIsValid()).isTrue();
        assertThat(node.getForceFence()).isFalse();
        assertThat(node.getIsActive()).isTrue();
        assertThat(node.getIsAutoGenerated()).isFalse();
        assertThat(node.getMembers()).isEmpty();
        assertThat(node.toString()).contains("Circle");
    }

    @Test
    void circleNodeSettersAndAccessors() {
        CircleNode node = new CircleNode();
        node.setId(2L);
        node.setName("N");
        node.setInviteCode("INV");
        node.setCreatedAt(789L);
        node.setLocationId("loc");
        node.setIsValid(false);
        node.setForceFence(true);
        node.setIsActive(false);
        node.setIsAutoGenerated(true);
        node.setMembers(Set.of());

        assertThat(node.getId()).isEqualTo(2L);
        assertThat(node.getName()).isEqualTo("N");
        assertThat(node.getInviteCode()).isEqualTo("INV");
        assertThat(node.getCreatedAt()).isEqualTo(789L);
        assertThat(node.getLocationId()).isEqualTo("loc");
        assertThat(node.getIsValid()).isFalse();
        assertThat(node.getForceFence()).isTrue();
        assertThat(node.getIsActive()).isFalse();
        assertThat(node.getIsAutoGenerated()).isTrue();
        assertThat(node.getMembers()).isEmpty();
    }

    @Test
    void circleNodeEqualsAndHashCode() {
        CircleNode n1 = CircleNode.builder().name("a").build();
        CircleNode n2 = CircleNode.builder().name("a").build();
        CircleNode n3 = CircleNode.builder().name("b").build();

        assertThat(n1).isEqualTo(n2);
        assertThat(n1.hashCode()).isEqualTo(n2.hashCode());
        assertThat(n1).isNotEqualTo(n3);
        assertThat(n1).isNotEqualTo(null);
    }

    @Test
    void userNodeBuilderAndAccessors() {
        UserNode node = UserNode.builder()
                .anonymousId("anon1")
                .status("ACTIVE")
                .statusUpdatedAt(123456L)
                .encounters(Set.of())
                .build();

        assertThat(node.getAnonymousId()).isEqualTo("anon1");
        assertThat(node.getStatus()).isEqualTo("ACTIVE");
        assertThat(node.getStatusUpdatedAt()).isEqualTo(123456L);
        assertThat(node.getEncounters()).isEmpty();
        assertThat(node.toString()).contains("anon1");
    }

    @Test
    void userNodeSettersAndAccessors() {
        UserNode node = new UserNode();
        node.setAnonymousId("anon2");
        node.setStatus("CONTAGIED");
        node.setStatusUpdatedAt(789L);
        node.setEncounters(Set.of());

        assertThat(node.getAnonymousId()).isEqualTo("anon2");
        assertThat(node.getStatus()).isEqualTo("CONTAGIED");
        assertThat(node.getStatusUpdatedAt()).isEqualTo(789L);
        assertThat(node.getEncounters()).isEmpty();
    }

    @Test
    void userNodeEqualsAndHashCode() {
        UserNode n1 = UserNode.builder().anonymousId("a").build();
        UserNode n2 = UserNode.builder().anonymousId("a").build();
        UserNode n3 = UserNode.builder().anonymousId("b").build();

        assertThat(n1).isEqualTo(n2);
        assertThat(n1.hashCode()).isEqualTo(n2.hashCode());
        assertThat(n1).isNotEqualTo(n3);
        assertThat(n1).isNotEqualTo(null);
    }

    @Test
    void encounterRelationshipBuilderAndAccessors() {
        EncounterRelationship rel = EncounterRelationship.builder()
                .id(1L)
                .startTime(1000L)
                .duration(60L)
                .locationId("loc1")
                .isValid(true)
                .forceFence(false)
                .build();

        assertThat(rel.getId()).isEqualTo(1L);
        assertThat(rel.getStartTime()).isEqualTo(1000L);
        assertThat(rel.getDuration()).isEqualTo(60L);
        assertThat(rel.getLocationId()).isEqualTo("loc1");
        assertThat(rel.isValid()).isTrue();
        assertThat(rel.isForceFence()).isFalse();
        assertThat(rel.toString()).contains("loc1");
    }

    @Test
    void encounterRelationshipSettersAndAccessors() {
        EncounterRelationship rel = new EncounterRelationship();
        rel.setId(2L);
        rel.setStartTime(2000L);
        rel.setDuration(120L);
        rel.setLocationId("loc2");
        rel.setValid(false);
        rel.setForceFence(true);

        assertThat(rel.getId()).isEqualTo(2L);
        assertThat(rel.getStartTime()).isEqualTo(2000L);
        assertThat(rel.getDuration()).isEqualTo(120L);
        assertThat(rel.getLocationId()).isEqualTo("loc2");
        assertThat(rel.isValid()).isFalse();
        assertThat(rel.isForceFence()).isTrue();
    }

    @Test
    void encounterRelationshipEqualsAndHashCode() {
        EncounterRelationship r1 = EncounterRelationship.builder().locationId("a").build();
        EncounterRelationship r2 = EncounterRelationship.builder().locationId("a").build();
        EncounterRelationship r3 = EncounterRelationship.builder().locationId("b").build();

        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
        assertThat(r1).isNotEqualTo(r3);
        assertThat(r1).isNotEqualTo(null);
    }
}
