package com.example.adyen.config;

import com.example.adyen.config.AdyenEnvironment.UnknownEnvironmentException;
import com.example.adyen.config.AdyenProperties.EnvConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 활성 환경 전환 — 자격증명이 채워지지 않은 환경으로 전환되는 사고를 막는 것이 핵심이다.
 * (운영 키를 받기 전에 live로 넘겨 두면, 원인을 알기 어려운 401/404가 난다)
 */
class AdyenEnvironmentTest {

    private static final EnvConfig TEST = new EnvConfig(
            "test-key", "TestMerchant", "https://checkout-test.adyen.com/v72",
            "test_client", "https://checkoutshopper-test.adyen.com");

    private static final EnvConfig LIVE = new EnvConfig(
            "live-key", "LiveMerchant", "https://x-y-checkout-live.adyenpayments.com/checkout/v72",
            "live_client", "https://checkoutshopper-live.adyen.com");

    /** 운영 키를 아직 안 받은 상태 — application.yml 기본값 그대로. */
    private static final EnvConfig LIVE_EMPTY = new EnvConfig(
            "", "", "", "", "https://checkoutshopper-live.adyen.com");

    private static AdyenEnvironment environment(EnvConfig live) {
        Map<String, EnvConfig> envs = new LinkedHashMap<>();
        envs.put("test", TEST);
        envs.put("live", live);
        return new AdyenEnvironment(
                new AdyenProperties("test", "http://localhost:8080/", envs));
    }

    @Test
    void 활성환경의_값을_읽는다() {
        AdyenEnvironment env = environment(LIVE);

        assertEquals("test", env.environment());
        assertFalse(env.isLive());
        assertEquals("TestMerchant", env.merchantAccount());
        assertEquals("test_client", env.clientKey());
        assertEquals("https://checkoutshopper-test.adyen.com", env.sdkBaseUrl());
        // returnUrl은 환경 공통이라 EnvConfig가 아니라 최상위에서 온다
        assertEquals("http://localhost:8080/", env.returnUrl());
    }

    @Test
    void 전환하면_이후_조회가_새_환경_값을_돌려준다() {
        AdyenEnvironment env = environment(LIVE);

        env.switchTo("live");

        assertEquals("live", env.environment());
        assertTrue(env.isLive());
        assertEquals("LiveMerchant", env.merchantAccount());
        assertEquals("live-key", env.apiKey());
        assertEquals("https://checkoutshopper-live.adyen.com", env.sdkBaseUrl());
    }

    @Test
    void 자격증명이_비어있는_환경으로는_전환할_수_없다() {
        AdyenEnvironment env = environment(LIVE_EMPTY);

        UnknownEnvironmentException e =
                assertThrows(UnknownEnvironmentException.class, () -> env.switchTo("live"));

        assertTrue(e.getMessage().contains("live"), e.getMessage());
        assertEquals("test", env.environment(), "실패한 전환은 활성 환경을 바꾸지 않아야 한다");
    }

    @Test
    void 없는_환경으로는_전환할_수_없다() {
        AdyenEnvironment env = environment(LIVE);

        assertThrows(UnknownEnvironmentException.class, () -> env.switchTo("staging"));
        assertEquals("test", env.environment());
    }

    @Test
    void active_environment가_environments에_없으면_부팅에_실패한다() {
        Map<String, EnvConfig> envs = Map.of("test", TEST);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new AdyenEnvironment(new AdyenProperties("live", "http://localhost:8080/", envs)));

        assertTrue(e.getMessage().contains("active-environment"), e.getMessage());
    }
}
