package com.example.adyen.controller;

import com.example.adyen.service.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionController.class)
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private SessionService sessionService;

    @Test
    void getSessionResult_proxiesToService() throws Exception {
        when(sessionService.getSessionResult("CS123", "RES"))
                .thenReturn(objectMapper.readTree("{\"id\":\"CS123\",\"status\":\"completed\"}"));

        mockMvc.perform(get("/api/sessions/CS123").param("sessionResult", "RES"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"));
        verify(sessionService).getSessionResult("CS123", "RES");
    }

    @Test
    void getSessionResult_requiresSessionResultParam() throws Exception {
        mockMvc.perform(get("/api/sessions/CS123")).andExpect(status().isBadRequest());
    }
}
