package com.circleguard.identity.controller;

import com.circleguard.identity.config.SecurityConfig;
import com.circleguard.identity.service.IdentityVaultService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IdentityVaultController.class)
@Import(SecurityConfig.class)
class IdentityVaultControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IdentityVaultService vaultService;

    @MockBean
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    @WithMockUser(authorities = "identity:lookup")
    void lookupIdentity_WithPermission_ReturnsRealIdentity() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        when(vaultService.resolveRealIdentity(anonymousId)).thenReturn("user@example.com");

        mockMvc.perform(get("/api/v1/identities/lookup/{id}", anonymousId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.realIdentity").value("user@example.com"));

        verify(kafkaTemplate).send(eq("audit.identity.accessed"), any());
    }

    @Test
    @WithMockUser(authorities = "other:permission")
    void lookupIdentity_WithoutPermission_Returns403() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        when(vaultService.resolveRealIdentity(anonymousId)).thenReturn("user@example.com");

        mockMvc.perform(get("/api/v1/identities/lookup/{id}", anonymousId))
                .andExpect(status().isForbidden());
    }

    @Test
    void lookupIdentity_Unauthenticated_Returns401() throws Exception {
        UUID anonymousId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/identities/lookup/{id}", anonymousId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "identity:lookup")
    void lookupIdentity_NotFound_Returns404ProblemDetail() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        when(vaultService.resolveRealIdentity(anonymousId))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Identity not found"));

        mockMvc.perform(get("/api/v1/identities/lookup/{id}", anonymousId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Identity not found"));

        verify(kafkaTemplate).send(eq("audit.identity.accessed"), any());
    }

    @Test
    void shouldMapIdentity() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        when(vaultService.getOrCreateAnonymousId("john@uni.edu")).thenReturn(anonymousId);

        mockMvc.perform(post("/api/v1/identities/map")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"realIdentity\": \"john@uni.edu\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anonymousId").value(anonymousId.toString()));
    }

    @Test
    void shouldRegisterVisitor() throws Exception {
        UUID anonymousId = UUID.randomUUID();
        when(vaultService.getOrCreateAnonymousId("VISITOR|guest@example.com|Guest|Meeting")).thenReturn(anonymousId);

        mockMvc.perform(post("/api/v1/identities/visitor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Guest\", \"email\": \"guest@example.com\", \"reason_for_visit\": \"Meeting\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anonymousId").value(anonymousId.toString()));
    }
}
