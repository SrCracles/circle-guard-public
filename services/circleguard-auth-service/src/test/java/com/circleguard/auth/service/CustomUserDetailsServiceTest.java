package com.circleguard.auth.service;

import com.circleguard.auth.model.LocalUser;
import com.circleguard.auth.model.Permission;
import com.circleguard.auth.model.Role;
import com.circleguard.auth.repository.LocalUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CustomUserDetailsServiceTest {

    @Mock
    private LocalUserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    private LocalUser activeUser;

    @BeforeEach
    void setUp() {
        Permission perm1 = new Permission();
        perm1.setName("READ_DASHBOARD");

        Role role = new Role();
        role.setName("ADMIN");
        role.setPermissions(Set.of(perm1));

        activeUser = new LocalUser();
        activeUser.setUsername("activeUser");
        activeUser.setPassword("secret");
        activeUser.setIsActive(true);
        activeUser.setRoles(Set.of(role));
    }

    @Test
    void shouldLoadUserByUsernameSuccessfully() {
        when(userRepository.findByUsername("activeUser")).thenReturn(Optional.of(activeUser));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("activeUser");

        assertThat(userDetails.getUsername()).isEqualTo("activeUser");
        assertThat(userDetails.getPassword()).isEqualTo("secret");
        assertThat(userDetails.isAccountNonExpired()).isTrue();
        assertThat(userDetails.isAccountNonLocked()).isTrue();
        assertThat(userDetails.isCredentialsNonExpired()).isTrue();
        assertThat(userDetails.isEnabled()).isTrue();

        Set<String> authorities = userDetails.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toSet());

        assertThat(authorities).contains("ROLE_ADMIN", "READ_DASHBOARD");
    }

    @Test
    void shouldThrowUsernameNotFoundExceptionWhenUserDoesNotExist() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("unknown"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void shouldThrowDisabledExceptionWhenUserIsInactive() {
        activeUser.setIsActive(false);
        when(userRepository.findByUsername("activeUser")).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("activeUser"))
                .isInstanceOf(DisabledException.class)
                .hasMessageContaining("User account is disabled");
    }

    @Test
    void shouldReturnEmptyAuthoritiesWhenUserHasNoRoles() {
        activeUser.setRoles(Set.of());
        when(userRepository.findByUsername("activeUser")).thenReturn(Optional.of(activeUser));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("activeUser");

        assertThat(userDetails.getAuthorities()).isEmpty();
    }
}
