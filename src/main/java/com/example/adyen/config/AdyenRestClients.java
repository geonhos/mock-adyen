package com.example.adyen.config;

import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 환경별로 미리 만들어 둔 {@link RestClient} 보관소.
 *
 * <p>{@code Map<String, RestClient>} 를 그대로 빈으로 노출하지 않는 이유: Spring에서
 * {@code Map<String, T>} 주입은 "타입이 T인 빈 전체(빈 이름 → 빈)"를 의미하므로,
 * 환경 이름을 키로 쓰려던 맵과 충돌한다. 전용 타입으로 감싼다.
 */
public class AdyenRestClients {

    private final Map<String, RestClient> byEnvironment;

    public AdyenRestClients(Map<String, RestClient> byEnvironment) {
        this.byEnvironment = Map.copyOf(byEnvironment);
    }

    public RestClient forEnvironment(String environment) {
        RestClient client = byEnvironment.get(environment);
        if (client == null) {
            throw new IllegalStateException("환경 '" + environment
                    + "' 의 Adyen 자격증명이 설정되지 않았습니다. application-local.yml 의 adyen.environments."
                    + environment + " 에 api-key / merchant-account / base-url 을 채우고 앱을 재시작하세요.");
        }
        return client;
    }
}
