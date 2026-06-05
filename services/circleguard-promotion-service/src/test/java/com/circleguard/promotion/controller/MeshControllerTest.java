package com.circleguard.promotion.controller;

import com.circleguard.promotion.repository.graph.UserNodeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeshController.class)
@AutoConfigureMockMvc(addFilters = false)
class MeshControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserNodeRepository userRepository;

    @Test
    void getMeshStats_Success() throws Exception {
        String anonymousId = "anon-123";
        
        when(userRepository.getConfirmedConnectionCount(anonymousId)).thenReturn(5L);
        when(userRepository.getUnconfirmedConnectionCount(anonymousId)).thenReturn(10L);

        mockMvc.perform(get("/api/v1/mesh/stats/{anonymousId}", anonymousId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmedCount").value(5))
                .andExpect(jsonPath("$.unconfirmedCount").value(10));
    }
}
