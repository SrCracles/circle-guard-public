package com.circleguard.identity.model;

import com.circleguard.identity.event.IdentityAccessEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class IdentityModelsTest {

    @Test
    void identityMappingBuilderAndAccessors() {
        UUID anonId = UUID.randomUUID();
        IdentityMapping mapping = IdentityMapping.builder()
                .anonymousId(anonId)
                .realIdentity("john.doe@uni.edu")
                .identityHash("abc123")
                .salt("salt123")
                .build();

        assertThat(mapping.getAnonymousId()).isEqualTo(anonId);
        assertThat(mapping.getRealIdentity()).isEqualTo("john.doe@uni.edu");
        assertThat(mapping.getIdentityHash()).isEqualTo("abc123");
        assertThat(mapping.getSalt()).isEqualTo("salt123");
        assertThat(mapping.toString()).contains("john.doe");
    }

    @Test
    void identityMappingSettersAndAccessors() {
        IdentityMapping mapping = new IdentityMapping();
        UUID anonId = UUID.randomUUID();
        mapping.setAnonymousId(anonId);
        mapping.setRealIdentity("real");
        mapping.setIdentityHash("hash");
        mapping.setSalt("salt");

        assertThat(mapping.getAnonymousId()).isEqualTo(anonId);
        assertThat(mapping.getRealIdentity()).isEqualTo("real");
        assertThat(mapping.getIdentityHash()).isEqualTo("hash");
        assertThat(mapping.getSalt()).isEqualTo("salt");
    }

    @Test
    void identityMappingEqualsAndHashCode() {
        IdentityMapping m1 = IdentityMapping.builder().realIdentity("a").build();
        IdentityMapping m2 = IdentityMapping.builder().realIdentity("a").build();
        IdentityMapping m3 = IdentityMapping.builder().realIdentity("b").build();

        assertThat(m1).isEqualTo(m2);
        assertThat(m1.hashCode()).isEqualTo(m2.hashCode());
        assertThat(m1).isNotEqualTo(m3);
        assertThat(m1).isNotEqualTo(null);
        assertThat(m1).isNotEqualTo("string");
    }

    @Test
    void identityAccessEventBuilderAndAccessors() {
        IdentityAccessEvent.IdentityAccessPayload payload = IdentityAccessEvent.IdentityAccessPayload.builder()
                .anonymousId(UUID.randomUUID())
                .requestingUser("admin")
                .accessStatus("GRANTED")
                .build();

        IdentityAccessEvent.IdentityAccessMetadata metadata = IdentityAccessEvent.IdentityAccessMetadata.builder()
                .correlationId("corr-1")
                .version(1)
                .build();

        IdentityAccessEvent event = IdentityAccessEvent.builder()
                .eventId("evt-1")
                .eventType("ACCESS")
                .timestamp(Instant.now())
                .source("identity-service")
                .payload(payload)
                .metadata(metadata)
                .build();

        assertThat(event.getEventId()).isEqualTo("evt-1");
        assertThat(event.getEventType()).isEqualTo("ACCESS");
        assertThat(event.getTimestamp()).isNotNull();
        assertThat(event.getSource()).isEqualTo("identity-service");
        assertThat(event.getPayload()).isEqualTo(payload);
        assertThat(event.getMetadata()).isEqualTo(metadata);
        assertThat(event.toString()).contains("ACCESS");
    }

    @Test
    void identityAccessEventEqualsAndHashCode() {
        IdentityAccessEvent e1 = IdentityAccessEvent.builder().eventId("1").build();
        IdentityAccessEvent e2 = IdentityAccessEvent.builder().eventId("1").build();
        IdentityAccessEvent e3 = IdentityAccessEvent.builder().eventId("2").build();

        assertThat(e1).isEqualTo(e2);
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
        assertThat(e1).isNotEqualTo(e3);
        assertThat(e1).isNotEqualTo(null);
        assertThat(e1).isNotEqualTo("string");
    }

    @Test
    void identityAccessPayloadBuilderAndAccessors() {
        UUID id = UUID.randomUUID();
        IdentityAccessEvent.IdentityAccessPayload p = IdentityAccessEvent.IdentityAccessPayload.builder()
                .anonymousId(id)
                .requestingUser("user")
                .accessStatus("DENIED")
                .build();

        assertThat(p.getAnonymousId()).isEqualTo(id);
        assertThat(p.getRequestingUser()).isEqualTo("user");
        assertThat(p.getAccessStatus()).isEqualTo("DENIED");
        assertThat(p.toString()).contains("DENIED");
    }

    @Test
    void identityAccessPayloadEqualsAndHashCode() {
        UUID id = UUID.randomUUID();
        IdentityAccessEvent.IdentityAccessPayload p1 = IdentityAccessEvent.IdentityAccessPayload.builder().anonymousId(id).build();
        IdentityAccessEvent.IdentityAccessPayload p2 = IdentityAccessEvent.IdentityAccessPayload.builder().anonymousId(id).build();
        IdentityAccessEvent.IdentityAccessPayload p3 = IdentityAccessEvent.IdentityAccessPayload.builder().anonymousId(UUID.randomUUID()).build();

        assertThat(p1).isEqualTo(p2);
        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
        assertThat(p1).isNotEqualTo(p3);
        assertThat(p1).isNotEqualTo(null);
    }

    @Test
    void identityAccessMetadataBuilderAndAccessors() {
        IdentityAccessEvent.IdentityAccessMetadata m = IdentityAccessEvent.IdentityAccessMetadata.builder()
                .correlationId("corr")
                .version(2)
                .build();

        assertThat(m.getCorrelationId()).isEqualTo("corr");
        assertThat(m.getVersion()).isEqualTo(2);
        assertThat(m.toString()).contains("corr");
    }

    @Test
    void identityAccessMetadataEqualsAndHashCode() {
        IdentityAccessEvent.IdentityAccessMetadata m1 = IdentityAccessEvent.IdentityAccessMetadata.builder().correlationId("c").build();
        IdentityAccessEvent.IdentityAccessMetadata m2 = IdentityAccessEvent.IdentityAccessMetadata.builder().correlationId("c").build();
        IdentityAccessEvent.IdentityAccessMetadata m3 = IdentityAccessEvent.IdentityAccessMetadata.builder().correlationId("d").build();

        assertThat(m1).isEqualTo(m2);
        assertThat(m1.hashCode()).isEqualTo(m2.hashCode());
        assertThat(m1).isNotEqualTo(m3);
        assertThat(m1).isNotEqualTo(null);
    }
}
