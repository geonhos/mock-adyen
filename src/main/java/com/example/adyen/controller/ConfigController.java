package com.example.adyen.controller;

import com.example.adyen.config.AdyenEnvironment;
import com.example.adyen.config.AdyenEnvironment.UnknownEnvironmentException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 콘솔 부트스트랩 설정 + 환경(test/live) 전환.
 *
 * <p>프론트는 로드 시 {@code GET /api/config} 로 활성 환경의 clientKey와 Web SDK CDN 호스트를
 * 받아 SDK를 동적으로 주입한다. 전환({@code POST /api/config/environment}) 후에는 반드시
 * 페이지를 리로드해야 한다 — 이미 로드된 {@code AdyenCheckout} 전역은 자기가 온 CDN 빌드에
 * 묶여 있어 다른 환경으로 갈아끼울 수 없다.
 *
 * <p>API key는 절대 내려보내지 않는다. clientKey는 원래 브라우저에 노출되는 값이라 무방하다.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final AdyenEnvironment environment;

    public ConfigController(AdyenEnvironment environment) {
        this.environment = environment;
    }

    @GetMapping
    public Map<String, Object> config() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("environment", environment.environment());
        body.put("live", environment.isLive());
        body.put("clientKey", environment.clientKey());
        body.put("merchantAccount", environment.merchantAccount());
        body.put("baseUrl", environment.baseUrl());
        body.put("sdkBaseUrl", environment.sdkBaseUrl());
        body.put("returnUrl", environment.returnUrl());
        List<Map<String, Object>> envs = environment.names().stream()
                .map(name -> {
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("name", name);
                    e.put("configured", environment.config(name).configured());
                    return e;
                })
                .toList();
        body.put("environments", envs);
        return body;
    }

    /** 프론트 하위 호환 — 활성 환경의 clientKey만 필요할 때. */
    @GetMapping("/client-key")
    public ResponseEntity<Map<String, String>> clientKey() {
        String key = environment.clientKey();
        if (key == null || key.isBlank()) {
            return ResponseEntity.status(503)
                    .body(Map.of("error", "client-key is not configured for environment: " + environment.environment()));
        }
        return ResponseEntity.ok(Map.of("clientKey", key));
    }

    /**
     * 활성 환경을 전환한다. 응답의 {@code reloadRequired} 는 항상 true — 프론트는 이 응답을 받고
     * {@code location.reload()} 를 호출해야 새 환경의 SDK가 로드된다.
     */
    @PostMapping("/environment")
    public Map<String, Object> switchEnvironment(@RequestBody Map<String, String> body) {
        String target = body.get("environment");
        if (target == null || target.isBlank()) {
            throw new UnknownEnvironmentException("environment 값이 필요합니다");
        }
        environment.switchTo(target.trim());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("environment", environment.environment());
        result.put("live", environment.isLive());
        result.put("reloadRequired", true);
        return result;
    }

    @ExceptionHandler(UnknownEnvironmentException.class)
    public ResponseEntity<Map<String, Object>> handleUnknown(UnknownEnvironmentException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("reason", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
