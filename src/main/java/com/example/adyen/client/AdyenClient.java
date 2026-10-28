package com.example.adyen.client;

import com.example.adyen.config.AdyenEnvironment;
import com.example.adyen.config.AdyenRestClients;
import com.example.adyen.dto.SessionRequest;
import com.example.adyen.dto.SessionResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Adyen Checkout API 호출. 지갑 등록(Sessions flow)에 필요한 세 경로만 둔다.
 * <ul>
 *   <li>{@code POST /sessions} — 세션 생성</li>
 *   <li>{@code GET /sessions/{id}?sessionResult=} — 세션 결과 조회</li>
 *   <li>{@code GET /storedPaymentMethods} — 저장 결제수단 조회(볼팅 결과 확인)</li>
 * </ul>
 * 결제 자체({@code /sessions/{id}/payments})는 Web SDK가 Adyen에 직접 보내므로 서버에는 없다.
 */
@Component
public class AdyenClient {

    private static final Logger log = LoggerFactory.getLogger(AdyenClient.class);

    private final AdyenRestClients restClients;
    private final AdyenEnvironment environment;

    public AdyenClient(AdyenRestClients restClients, AdyenEnvironment environment) {
        this.restClients = restClients;
        this.environment = environment;
    }

    /**
     * 활성 환경(test/live)의 클라이언트를 <b>호출 시점에</b> 고른다.
     * 필드로 붙잡아 두면 런타임 환경 전환이 반영되지 않는다.
     */
    private RestClient client() {
        return restClients.forEnvironment(environment.environment());
    }

    public SessionResponse createSession(SessionRequest request) {
        log.debug("Calling Adyen /sessions with reference={}", request.reference());
        try {
            return client().post()
                    .uri("/sessions")
                    .body(request)
                    .retrieve()
                    .body(SessionResponse.class);
        } catch (RestClientResponseException e) {
            log.error("Adyen /sessions error: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new AdyenApiException(
                    "Adyen /sessions failed: " + e.getStatusCode(),
                    e.getResponseBodyAsString(),
                    e);
        }
    }

    /** Checkout {@code GET /sessions/{sessionId}?sessionResult=...} — 세션 상태/결과 조회(원본 JSON). */
    public JsonNode getSessionResult(String sessionId, String sessionResult) {
        log.debug("Calling Adyen GET /sessions/{}", sessionId);
        try {
            return client().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/sessions/{id}")
                            .queryParam("sessionResult", sessionResult)
                            .build(sessionId))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.error("Adyen GET /sessions/{} error: status={}, body={}",
                    sessionId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new AdyenApiException(
                    "Adyen GET /sessions failed: " + e.getStatusCode(),
                    e.getResponseBodyAsString(),
                    e);
        }
    }

    /**
     * Checkout API: shopperReference에 저장된 결제수단 목록 조회.
     * 응답은 원본 JSON 그대로 반환한다.
     *
     * @see <a href="https://docs.adyen.com/api-explorer/Checkout/latest/get/storedPaymentMethods">GET /storedPaymentMethods</a>
     */
    public JsonNode getStoredPaymentMethods(String merchantAccount, String shopperReference) {
        log.debug("Calling Adyen GET /storedPaymentMethods for shopperReference={}", shopperReference);
        try {
            return client().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/storedPaymentMethods")
                            .queryParam("merchantAccount", merchantAccount)
                            .queryParam("shopperReference", shopperReference)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            log.error("Adyen GET /storedPaymentMethods error: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new AdyenApiException(
                    "Adyen GET /storedPaymentMethods failed: " + e.getStatusCode(),
                    e.getResponseBodyAsString(),
                    e);
        }
    }

    public static class AdyenApiException extends RuntimeException {
        private final String responseBody;

        public AdyenApiException(String message, String responseBody, Throwable cause) {
            super(message, cause);
            this.responseBody = responseBody;
        }

        public String getResponseBody() {
            return responseBody;
        }
    }
}
