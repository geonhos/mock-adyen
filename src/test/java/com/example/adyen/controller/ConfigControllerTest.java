package com.example.adyen.controller;

import com.example.adyen.config.AdyenEnvironment;
import com.example.adyen.config.AdyenProperties;
import com.example.adyen.config.AdyenProperties.EnvConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/config} — 콘솔 부트스트랩 값과 환경 전환.
 * 실제 {@link AdyenEnvironment} 를 태워 전환이 이후 조회에 반영되는지까지 본다.
 */
@WebMvcTest(ConfigController.class)
@Import(ConfigControllerTest.TestEnvironment.class)
class ConfigControllerTest {

    @TestConfiguration
    static class TestEnvironment {
        @Bean
        AdyenEnvironment adyenEnvironment() {
            Map<String, EnvConfig> envs = new LinkedHashMap<>();
            envs.put("test", new EnvConfig("test-key", "TestMerchant",
                    "https://checkout-test.adyen.com/v72", "test_client",
                    "https://checkoutshopper-test.adyen.com"));
            envs.put("live", new EnvConfig("live-key", "LiveMerchant",
                    "https://x-y-checkout-live.adyenpayments.com/checkout/v72", "live_client",
                    "https://checkoutshopper-live.adyen.com"));
            return new AdyenEnvironment(
                    new AdyenProperties("test", "http://localhost:8080/", envs));
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired AdyenEnvironment environment;

    /** 스프링 컨텍스트가 재사용되므로, 전환 테스트가 다른 테스트에 새지 않게 되돌린다. */
    @AfterEach
    void resetEnvironment() {
        environment.switchTo("test");
    }

    @Test
    void config는_활성환경의_부트스트랩_값을_돌려준다() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.environment").value("test"))
                .andExpect(jsonPath("$.live").value(false))
                .andExpect(jsonPath("$.clientKey").value("test_client"))
                .andExpect(jsonPath("$.sdkBaseUrl").value("https://checkoutshopper-test.adyen.com"))
                .andExpect(jsonPath("$.environments[0].name").value("live"))
                .andExpect(jsonPath("$.environments[1].name").value("test"));
    }

    @Test
    void config는_api_key를_노출하지_않는다() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").doesNotExist());
    }

    @Test
    void 환경을_전환하면_이후_config가_새_환경을_돌려준다() throws Exception {
        mockMvc.perform(post("/api/config/environment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"environment\":\"live\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.environment").value("live"))
                .andExpect(jsonPath("$.reloadRequired").value(true));

        mockMvc.perform(get("/api/config"))
                .andExpect(jsonPath("$.environment").value("live"))
                .andExpect(jsonPath("$.live").value(true))
                .andExpect(jsonPath("$.clientKey").value("live_client"))
                .andExpect(jsonPath("$.sdkBaseUrl").value("https://checkoutshopper-live.adyen.com"));
    }

    @Test
    void 없는_환경으로_전환하면_이유와_함께_400() throws Exception {
        mockMvc.perform(post("/api/config/environment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"environment\":\"staging\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ok").value(false))
                .andExpect(jsonPath("$.reason").exists());
    }
}
