package com.circleguard.auth.controller;

import com.circleguard.auth.model.LocalUser;
import com.circleguard.auth.repository.LocalUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private LocalUserRepository localUserRepository;

    @Test
    @WithMockUser
    void shouldReturnUsersByPermission() throws Exception {
        LocalUser user = new LocalUser();
        user.setUsername("admin");
        user.setEmail("admin@circleguard.com");

        when(localUserRepository.findUsersByPermissionName("ALERT_ADMIN"))
                .thenReturn(List.of(user));

        mockMvc.perform(get("/api/v1/users/permissions/ALERT_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("admin"))
                .andExpect(jsonPath("$[0].email").value("admin@circleguard.com"));
    }

    @Test
    @WithMockUser
    void shouldReturnEmptyListWhenNoUsersHavePermission() throws Exception {
        when(localUserRepository.findUsersByPermissionName("UNKNOWN"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/users/permissions/UNKNOWN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser
    void shouldHandleNullEmail() throws Exception {
        LocalUser user = new LocalUser();
        user.setUsername("user1");
        user.setEmail(null);

        when(localUserRepository.findUsersByPermissionName("SOME_PERM"))
                .thenReturn(List.of(user));

        mockMvc.perform(get("/api/v1/users/permissions/SOME_PERM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value(""));
    }
}
