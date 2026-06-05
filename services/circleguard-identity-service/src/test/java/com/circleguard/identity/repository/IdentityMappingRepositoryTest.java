package com.circleguard.identity.repository;

import com.circleguard.identity.model.IdentityMapping;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class IdentityMappingRepositoryTest {

    @Autowired
    private IdentityMappingRepository repository;

    @Test
    void shouldSaveAndFindByIdentityHash() {
        IdentityMapping mapping = IdentityMapping.builder()
                .realIdentity("user@uni.edu")
                .identityHash("hash123")
                .salt("salt123")
                .build();

        IdentityMapping saved = repository.save(mapping);
        Optional<IdentityMapping> found = repository.findByIdentityHash("hash123");

        assertThat(found).isPresent();
        assertThat(found.get().getRealIdentity()).isEqualTo("user@uni.edu");
        assertThat(found.get().getAnonymousId()).isEqualTo(saved.getAnonymousId());
    }

    @Test
    void shouldFindById() {
        IdentityMapping mapping = IdentityMapping.builder()
                .realIdentity("guest@uni.edu")
                .identityHash("hash456")
                .salt("salt456")
                .build();

        IdentityMapping saved = repository.save(mapping);
        Optional<IdentityMapping> found = repository.findById(saved.getAnonymousId());

        assertThat(found).isPresent();
        assertThat(found.get().getIdentityHash()).isEqualTo("hash456");
    }
}
