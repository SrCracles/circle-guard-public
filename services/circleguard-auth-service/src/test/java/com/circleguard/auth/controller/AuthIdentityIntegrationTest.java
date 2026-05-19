package com.circleguard.auth.controller;

import com.circleguard.auth.client.IdentityClient;
import com.circleguard.auth.security.SecurityConfig;
import com.circleguard.auth.service.CustomUserDetailsService;
import com.circleguard.auth.service.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LoginController.class)
@Import(SecurityConfig.class)
class AuthIdentityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthenticationManager authManager;

    @MockBean
    private JwtTokenService jwtService;

    @MockBean
    private IdentityClient identityClient;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @Test
    void loginSuccessful_CreatesAnonymousMapping() throws Exception {
        String username = "student@uni.edu";
        String password = "password123";
        UUID anonymousId = UUID.randomUUID();
        String jwtToken = "mock-jwt-token-123";

        Authentication auth = new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(identityClient.getAnonymousId(username)).thenReturn(anonymousId);
        when(jwtService.generateToken(eq(anonymousId), any(Authentication.class))).thenReturn(jwtToken);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(jwtToken))
                .andExpect(jsonPath("$.anonymousId").value(anonymousId.toString()))
                .andExpect(jsonPath("$.type").value("Bearer"));

        verify(authManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(identityClient).getAnonymousId(username);
        verify(jwtService).generateToken(eq(anonymousId), any(Authentication.class));
    }

    @Test
    void loginFailed_DoesNotCallIdentityService() throws Exception {
        String username = "invalid@uni.edu";
        String password = "wrongpass";

        when(authManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new org.springframework.security.authentication.BadCredentialsException("Invalid"));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));

        verify(identityClient, never()).getAnonymousId(anyString());
        verify(jwtService, never()).generateToken(any(), any());
    }
}
