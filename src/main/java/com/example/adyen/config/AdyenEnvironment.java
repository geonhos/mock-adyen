package com.example.adyen.config;

import com.example.adyen.config.AdyenProperties.EnvConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 활성 Adyen 환경(test/live)을 보유하고, 앱 재시작 없이 전환한다.
 *
 * <p>모든 소비자(서비스/컨트롤러)는 {@link AdyenProperties} 대신 이 컴포넌트를 주입받아
 * {@code merchantAccount()} 처럼 읽는다 — 읽는 시점의 활성 환경 값이 나온다.
 *
 * <p>전환은 {@code POST /api/config/environment} 로 이뤄지며, 브라우저는 페이지를 리로드해야
 * 한다(로드된 Adyen Web SDK는 자기가 온 CDN 빌드에 묶여 있어 핫스왑되지 않는다).
 */
@Component
public class AdyenEnvironment {

    private static final Logger log = LoggerFactory.getLogger(AdyenEnvironment.class);

    private final Map<String, EnvConfig> environments;
    private final String returnUrl;
    private final AtomicReference<String> active = new AtomicReference<>();

    public AdyenEnvironment(AdyenProperties properties) {
        Map<String, EnvConfig> envs = properties.environments();
        if (envs == null || envs.isEmpty()) {
            throw new IllegalStateException(
                    "adyen.environments 가 비어 있습니다. application-local.yml 에 test/live 블록을 설정하세요.");
        }
        this.environments = Map.copyOf(envs);
        this.returnUrl = properties.returnUrl();

        String initial = properties.activeEnvironment();
        if (initial == null || !environments.containsKey(initial)) {
            throw new IllegalStateException("adyen.active-environment=" + initial
                    + " 가 adyen.environments 에 없습니다. 사용 가능: " + names());
        }
        this.active.set(initial);
        log.info("Adyen environment 초기값={} (사용 가능={})", initial, names());
    }

    /** 활성 환경 이름 (test | live). */
    public String environment() {
        return active.get();
    }

    public boolean isLive() {
        return "live".equalsIgnoreCase(active.get());
    }

    /** 설정에 존재하는 환경 이름들 — 콘솔 셀렉터용. */
    public List<String> names() {
        return environments.keySet().stream().sorted().toList();
    }

    public EnvConfig config() {
        return config(active.get());
    }

    public EnvConfig config(String name) {
        EnvConfig cfg = environments.get(name);
        if (cfg == null) {
            throw new UnknownEnvironmentException("알 수 없는 환경: " + name + " (사용 가능: " + names() + ")");
        }
        return cfg;
    }

    /**
     * 활성 환경을 바꾼다.
     *
     * @throws UnknownEnvironmentException 없는 환경이거나, 자격증명이 비어 있는 환경일 때
     */
    public void switchTo(String name) {
        EnvConfig cfg = config(name);
        if (!cfg.configured()) {
            throw new UnknownEnvironmentException("환경 '" + name
                    + "' 의 자격증명이 비어 있습니다. application-local.yml 의 adyen.environments." + name
                    + " 에 api-key / merchant-account / base-url 을 채우세요.");
        }
        String previous = active.getAndSet(name);
        log.warn("Adyen environment 전환: {} → {}", previous, name);
    }

    /* 활성 환경 값 조회 */

    public String apiKey() {
        return config().apiKey();
    }

    public String merchantAccount() {
        return config().merchantAccount();
    }

    public String baseUrl() {
        return config().baseUrl();
    }

    public String clientKey() {
        return config().clientKey();
    }

    public String sdkBaseUrl() {
        return config().sdkBaseUrl();
    }

    /** 환경 공통 값이므로 {@link EnvConfig} 가 아니라 최상위 설정에서 온다. */
    public String returnUrl() {
        return returnUrl;
    }

    /** 존재하지 않거나 사용할 수 없는 환경 — 400으로 매핑된다. */
    public static class UnknownEnvironmentException extends RuntimeException {
        public UnknownEnvironmentException(String message) {
            super(message);
        }
    }
}
