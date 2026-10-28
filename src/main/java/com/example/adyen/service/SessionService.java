package com.example.adyen.service;

import com.example.adyen.client.AdyenClient;
import com.example.adyen.config.AdyenEnvironment;
import com.example.adyen.dto.Amount;
import com.example.adyen.dto.SessionRequest;
import com.example.adyen.dto.SessionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

    private final AdyenClient adyenClient;
    private final AdyenEnvironment properties;

    public SessionService(AdyenClient adyenClient, AdyenEnvironment properties) {
        this.adyenClient = adyenClient;
        this.properties = properties;
    }

    public SessionResponse createSession(String reference, Long amount, String currency,
                                          String shopperReference, String countryCode,
                                          String recurringProcessingModel) {
        SessionRequest request = new SessionRequest(
                new Amount(currency, amount),
                reference,
                properties.returnUrl(),
                properties.merchantAccount(),
                countryCode,
                shopperReference,
                "Web",
                true,
                (recurringProcessingModel == null || recurringProcessingModel.isBlank())
                        ? "CardOnFile" : recurringProcessingModel,
                "Ecommerce"
        );
        return adyenClient.createSession(request);
    }

    /** Adyen {@code GET /sessions/{id}?sessionResult=...} 프록시 (원본 JSON). */
    public JsonNode getSessionResult(String sessionId, String sessionResult) {
        return adyenClient.getSessionResult(sessionId, sessionResult);
    }
}
