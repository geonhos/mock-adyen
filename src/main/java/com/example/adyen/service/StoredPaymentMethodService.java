package com.example.adyen.service;

import com.example.adyen.client.AdyenClient;
import com.example.adyen.config.AdyenEnvironment;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 저장 결제수단 조회. Sessions flow에서는 결제 요청을 SDK가 Adyen에 직접 보내므로
 * 서버는 {@code storedPaymentMethodId}를 콜백으로 받지 못한다. 볼팅 결과는 이 조회로 확인한다.
 */
@Service
public class StoredPaymentMethodService {

    private final AdyenClient adyenClient;
    private final AdyenEnvironment environment;

    public StoredPaymentMethodService(AdyenClient adyenClient, AdyenEnvironment environment) {
        this.adyenClient = adyenClient;
        this.environment = environment;
    }

    /**
     * {@code GET /storedPaymentMethods?merchantAccount=…&shopperReference=…}.
     * 콘솔 인스펙터용으로 서버가 보낸 요청 요약과 Adyen 원본 응답을 함께 돌려준다.
     */
    public Map<String, Object> list(String shopperReference) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", "GET");
        request.put("path", "/storedPaymentMethods");
        request.put("merchantAccount", environment.merchantAccount());
        request.put("shopperReference", shopperReference);

        JsonNode response = adyenClient.getStoredPaymentMethods(environment.merchantAccount(), shopperReference);

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("request", request);
        view.put("response", response);
        return view;
    }
}
