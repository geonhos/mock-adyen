package com.example.adyen.config;

import com.example.adyen.client.AdyenExchangeInterceptor;
import com.example.adyen.client.AdyenExchangeLog;
import com.example.adyen.config.AdyenProperties.EnvConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class RestClientConfig {

    private static final Logger log = LoggerFactory.getLogger(RestClientConfig.class);

    /**
     * 자격증명이 채워진 환경마다 {@link RestClient} 를 하나씩 만들어 둔다. baseUrl과 API key는
     * 클라이언트에 구워지므로 환경 전환은 "다른 클라이언트를 고르는 것"이 된다.
     *
     * <p>미기입 환경(예: 운영 키를 아직 안 받은 상태)은 건너뛴다 — 부팅은 성공하고,
     * 그 환경을 실제로 쓰려 할 때 {@link AdyenRestClients#forEnvironment} 가 이유를 알려준다.
     */
    @Bean
    public AdyenRestClients adyenRestClients(AdyenProperties properties, AdyenExchangeLog exchangeLog) {
        Map<String, RestClient> clients = new LinkedHashMap<>();
        properties.environments().forEach((name, cfg) -> {
            if (cfg.configured()) {
                clients.put(name, build(cfg, exchangeLog));
            } else {
                log.warn("Adyen environment '{}' 자격증명 미설정 — 이 환경으로는 호출할 수 없습니다", name);
            }
        });
        return new AdyenRestClients(clients);
    }

    private static RestClient build(EnvConfig cfg, AdyenExchangeLog exchangeLog) {
        return RestClient.builder()
                // 인터셉터가 응답 본문을 읽고도 하위 코드가 다시 읽을 수 있어야 한다 → buffering 필수
                .requestFactory(new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory()))
                .baseUrl(cfg.baseUrl())
                .defaultHeader("x-API-key", cfg.apiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor(new AdyenExchangeInterceptor(exchangeLog))
                .build();
    }
}
