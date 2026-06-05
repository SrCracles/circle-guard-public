package com.circleguard.identity.service;

import com.circleguard.identity.model.IdentityMapping;
import com.circleguard.identity.repository.IdentityMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class IdentityVaultServiceTest {

    @Mock
    private IdentityMappingRepository repository;

    @InjectMocks
    private IdentityVaultService vaultService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(vaultService, "hashSalt", "test-salt");
    }

    @Test
    void shouldReturnExistingAnonymousId() {
        UUID existingId = UUID.randomUUID();
        IdentityMapping mapping = IdentityMapping.builder()
                .anonymousId(existingId)
                .realIdentity("user@uni.edu")
                .identityHash(anyHash())
                .salt("salt")
                .build();

        when(repository.findByIdentityHash(any())).thenReturn(Optional.of(mapping));

        UUID result = vaultService.getOrCreateAnonymousId("user@uni.edu");

        assertThat(result).isEqualTo(existingId);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldCreateNewAnonymousIdWhenNotFound() {
        when(repository.findByIdentityHash(any())).thenReturn(Optional.empty());
        when(repository.save(any(IdentityMapping.class))).thenAnswer(inv -> {
            IdentityMapping m = inv.getArgument(0);
            if (m.getAnonymousId() == null) {
                m.setAnonymousId(UUID.randomUUID());
            }
            return m;
        });

        UUID result = vaultService.getOrCreateAnonymousId("new@uni.edu");

        assertThat(result).isNotNull();
        verify(repository).save(any(IdentityMapping.class));
    }

    @Test
    void shouldResolveRealIdentity() {
        UUID anonId = UUID.randomUUID();
        IdentityMapping mapping = IdentityMapping.builder()
                .anonymousId(anonId)
                .realIdentity("resolved@uni.edu")
                .identityHash("hash")
                .salt("salt")
                .build();

        when(repository.findById(anonId)).thenReturn(Optional.of(mapping));

        String result = vaultService.resolveRealIdentity(anonId);

        assertThat(result).isEqualTo("resolved@uni.edu");
    }

    @Test
    void shouldThrow404WhenIdentityNotFound() {
        UUID anonId = UUID.randomUUID();
        when(repository.findById(anonId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vaultService.resolveRealIdentity(anonId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Identity not found");
    }

    private String anyHash() {
        return "any-hash-string";
    }
}
