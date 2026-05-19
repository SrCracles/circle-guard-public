package com.circleguard.auth.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DualChainAuthenticationProviderTest {

    private LdapAuthenticationProvider ldapProvider;
    private DaoAuthenticationProvider localProvider;
    private DualChainAuthenticationProvider dualChainProvider;

    @BeforeEach
    void setUp() {
        ldapProvider = mock(LdapAuthenticationProvider.class);
        localProvider = mock(DaoAuthenticationProvider.class);
        dualChainProvider = new DualChainAuthenticationProvider(ldapProvider, localProvider);
    }

    @Test
    void shouldAuthenticateWithLdapWhenAvailable() {
        Authentication token = new UsernamePasswordAuthenticationToken("user", "pass");
        Authentication ldapResult = new UsernamePasswordAuthenticationToken("user", null, java.util.Collections.emptyList());

        when(ldapProvider.authenticate(token)).thenReturn(ldapResult);

        Authentication result = dualChainProvider.authenticate(token);

        assertNotNull(result);
        assertEquals(ldapResult, result);
        verify(ldapProvider).authenticate(token);
        verify(localProvider, never()).authenticate(any());
    }

    @Test
    void shouldFallbackToLocalWhenLdapFails() {
        Authentication token = new UsernamePasswordAuthenticationToken("user", "pass");
        Authentication localResult = new UsernamePasswordAuthenticationToken("user", null, java.util.Collections.emptyList());

        when(ldapProvider.authenticate(token)).thenThrow(new BadCredentialsException("LDAP failed"));
        when(localProvider.authenticate(token)).thenReturn(localResult);

        Authentication result = dualChainProvider.authenticate(token);

        assertNotNull(result);
        assertEquals(localResult, result);
        verify(ldapProvider).authenticate(token);
        verify(localProvider).authenticate(token);
    }

    @Test
    void shouldThrowWhenBothProvidersFail() {
        Authentication token = new UsernamePasswordAuthenticationToken("user", "pass");

        when(ldapProvider.authenticate(token)).thenThrow(new BadCredentialsException("LDAP failed"));
        when(localProvider.authenticate(token)).thenThrow(new BadCredentialsException("Local failed"));

        assertThrows(BadCredentialsException.class, () -> dualChainProvider.authenticate(token));
    }

    @Test
    void shouldSupportUsernamePasswordAuthenticationToken() {
        assertTrue(dualChainProvider.supports(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void shouldNotSupportOtherAuthenticationTypes() {
        assertFalse(dualChainProvider.supports(String.class));
    }
}
