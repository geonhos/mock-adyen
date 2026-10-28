package com.example.adyen.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Adyen으로 나가는 실제 HTTP 요청/응답을 {@link AdyenExchangeLog} 에 기록한다.
 *
 * <p>응답 본문을 읽고도 하위 코드가 다시 읽을 수 있어야 하므로,
 * {@code RestClient} 는 반드시 buffering request factory 로 구성돼야 한다
 * ({@code RestClientConfig} 참조).
 *
 * <p>기록 실패가 실제 호출을 깨뜨리지 않도록 모든 기록 경로를 방어한다.
 */
public class AdyenExchangeInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AdyenExchangeInterceptor.class);

    /** 값이 노출되면 안 되는 헤더 (소문자 비교). */
    private static final Set<String> REDACTED = Set.of("x-api-key", "authorization", "cookie", "set-cookie");

    private static final String MASK = "***redacted***";

    private final AdyenExchangeLog exchangeLog;

    public AdyenExchangeInterceptor(AdyenExchangeLog exchangeLog) {
        this.exchangeLog = exchangeLog;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        long startedAt = System.currentTimeMillis();
        String requestBody = body.length == 0 ? null : new String(body, StandardCharsets.UTF_8);

        ClientHttpResponse response;
        try {
            response = execution.execute(request, body);
        } catch (IOException | RuntimeException e) {
            safeRecord(request, requestBody, null, null, null,
                    System.currentTimeMillis() - startedAt, e.toString());
            throw e;
        }

        Integer status = null;
        Map<String, String> responseHeaders = null;
        String responseBody = null;
        String error = null;
        try {
            status = response.getStatusCode().value();
            responseHeaders = flatten(response.getHeaders());
            // buffering factory 덕분에 여기서 읽어도 하위에서 다시 읽을 수 있다.
            responseBody = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            error = "응답 기록 실패: " + e;
        }
        safeRecord(request, requestBody, status, responseHeaders, responseBody,
                System.currentTimeMillis() - startedAt, error);
        return response;
    }

    private void safeRecord(HttpRequest request, String requestBody, Integer status,
                            Map<String, String> responseHeaders, String responseBody,
                            long durationMs, String error) {
        try {
            exchangeLog.record(
                    request.getMethod().name(),
                    request.getURI().toString(),
                    flatten(request.getHeaders()),
                    requestBody,
                    status,
                    responseHeaders,
                    responseBody,
                    durationMs,
                    error);
        } catch (Throwable e) {
            // 기록은 부가 기능이다. 절대 실제 호출을 깨뜨리지 않는다.
            log.warn("Adyen 교환 기록 실패 (호출 자체에는 영향 없음)", e);
        }
    }

    /** 멀티값 헤더를 표시용 문자열로 평탄화하고 민감 헤더는 마스킹한다. */
    private static Map<String, String> flatten(HttpHeaders headers) {
        Map<String, String> out = new LinkedHashMap<>();
        headers.forEach((name, values) -> out.put(name, maskIfSensitive(name, values)));
        return out;
    }

    private static String maskIfSensitive(String name, List<String> values) {
        if (REDACTED.contains(name.toLowerCase(Locale.ROOT))) {
            return MASK;
        }
        return String.join(", ", values);
    }
}
