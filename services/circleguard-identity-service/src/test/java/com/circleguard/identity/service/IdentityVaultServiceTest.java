package com.circleguard.identity.service;

import com.circleguard.identity.model.IdentityMapping;
import com.circleguard.identity.repository.IdentityMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityVaultServiceTest {

    private IdentityMappingRepository repository;
    private IdentityVaultService service;

    @BeforeEach
    void setUp() {
        repository = mock(IdentityMappingRepository.class);
        service = new IdentityVaultService(repository);
        ReflectionTestUtils.setField(service, "hashSalt", "test-salt");
    }

    @Test
    void shouldReturnExistingAnonymousIdForKnownIdentity() {
        String realIdentity = "student@uni.edu";
        UUID existingId = UUID.randomUUID();
        IdentityMapping mapping = IdentityMapping.builder()
                .anonymousId(existingId)
                .build();

        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.of(mapping));

        UUID result = service.getOrCreateAnonymousId(realIdentity);

        assertEquals(existingId, result);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldCreateNewMappingForUnknownIdentity() {
        String realIdentity = "newstudent@uni.edu";

        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> {
            IdentityMapping m = inv.getArgument(0);
            m.setAnonymousId(UUID.randomUUID());
            return m;
        });

        UUID result = service.getOrCreateAnonymousId(realIdentity);

        assertNotNull(result);
        ArgumentCaptor<IdentityMapping> captor = ArgumentCaptor.forClass(IdentityMapping.class);
        verify(repository).save(captor.capture());
        assertEquals(realIdentity, captor.getValue().getRealIdentity());
        assertNotNull(captor.getValue().getSalt());
    }

    @Test
    void shouldGenerateConsistentHashForSameIdentity() {
        String realIdentity = "consistent@uni.edu";

        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID first = service.getOrCreateAnonymousId(realIdentity);

        // Reset mock to simulate second call finding the mapping
        IdentityMapping mapping = IdentityMapping.builder()
                .anonymousId(first)
                .build();
        when(repository.findByIdentityHash(anyString())).thenReturn(Optional.of(mapping));

        UUID second = service.getOrCreateAnonymousId(realIdentity);

        assertEquals(first, second);
    }

    @Test
    void shouldResolveRealIdentityForKnownAnonymousId() {
        UUID anonymousId = UUID.randomUUID();
        IdentityMapping mapping = IdentityMapping.builder()
                .anonymousId(anonymousId)
                .realIdentity("found@uni.edu")
                .build();

        when(repository.findById(anonymousId)).thenReturn(Optional.of(mapping));

        String result = service.resolveRealIdentity(anonymousId);

        assertEquals("found@uni.edu", result);
    }

    @Test
    void shouldThrowNotFoundWhenResolvingUnknownAnonymousId() {
        UUID anonymousId = UUID.randomUUID();

        when(repository.findById(anonymousId)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.resolveRealIdentity(anonymousId));

        assertEquals(404, exception.getStatusCode().value());
    }
}
