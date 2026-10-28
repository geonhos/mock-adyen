package com.example.adyen.controller;

import com.example.adyen.service.StoredPaymentMethodService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StoredPaymentMethodController.class)
class StoredPaymentMethodControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private StoredPaymentMethodService service;

    @Test
    void list_proxiesToService() throws Exception {
        when(service.list("shopper-1")).thenReturn(Map.of("response", Map.of("storedPaymentMethods", java.util.List.of())));

        mockMvc.perform(get("/api/stored-payment-methods").param("shopperReference", "shopper-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response.storedPaymentMethods").isArray());
        verify(service).list("shopper-1");
    }

    @Test
    void list_requiresShopperReference() throws Exception {
        mockMvc.perform(get("/api/stored-payment-methods")).andExpect(status().isBadRequest());
    }
}
