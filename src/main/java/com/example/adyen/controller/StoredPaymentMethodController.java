package com.example.adyen.controller;

import com.example.adyen.client.AdyenClient.AdyenApiException;
import com.example.adyen.service.StoredPaymentMethodService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 저장된 결제수단(토큰) 조회.
 * Adyen 매핑: {@code GET /v72/storedPaymentMethods?merchantAccount=...&shopperReference=...}
 */
@RestController
@Validated
@RequestMapping("/api/stored-payment-methods")
public class StoredPaymentMethodController {

    private final StoredPaymentMethodService service;

    public StoredPaymentMethodController(StoredPaymentMethodService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(@RequestParam @NotBlank String shopperReference) {
        return ResponseEntity.ok(service.list(shopperReference));
    }

    @ExceptionHandler(AdyenApiException.class)
    public ResponseEntity<String> handleAdyen(AdyenApiException e) {
        return ResponseEntity.status(502).body(e.getResponseBody());
    }
}
