package com.circleguard.auth.model;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthModelsTest {

    @Test
    void localUserBuilderAndAccessors() {
        UUID id = UUID.randomUUID();
        Role role = Role.builder().name("ADMIN").build();
        LocalUser user = LocalUser.builder()
                .id(id)
                .username("test")
                .password("secret")
                .email("a@b.com")
                .isActive(true)
                .roles(Set.of(role))
                .build();

        assertThat(user.getId()).isEqualTo(id);
        assertThat(user.getUsername()).isEqualTo("test");
        assertThat(user.getPassword()).isEqualTo("secret");
        assertThat(user.getEmail()).isEqualTo("a@b.com");
        assertThat(user.getIsActive()).isTrue();
        assertThat(user.getRoles()).containsExactly(role);
        assertThat(user.toString()).contains("test");
    }

    @Test
    void rolePermissions() {
        Permission p = Permission.builder().name("READ").build();
        Role r = Role.builder().name("USER").permissions(Set.of(p)).build();

        assertThat(r.getPermissions()).containsExactly(p);
        assertThat(r.hashCode()).isNotZero();
    }

    @Test
    void permissionEqualsAndHashCode() {
        Permission p1 = Permission.builder().name("WRITE").build();
        Permission p2 = Permission.builder().name("WRITE").build();

        assertThat(p1).isEqualTo(p2);
        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
    }

    @Test
    void localUserEquals() {
        LocalUser u1 = LocalUser.builder().username("x").build();
        LocalUser u2 = LocalUser.builder().username("x").build();

        assertThat(u1).isEqualTo(u2);
    }
}
