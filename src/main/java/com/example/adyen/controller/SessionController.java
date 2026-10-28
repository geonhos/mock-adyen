package com.example.adyen.controller;

import com.example.adyen.client.AdyenClient.AdyenApiException;
import com.example.adyen.dto.SessionBody;
import com.example.adyen.dto.SessionResponse;
import com.example.adyen.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/sessions")
    public ResponseEntity<SessionResponse> createSession(@Valid @RequestBody SessionBody body) {
        String countryCode = body.countryCode() != null ? body.countryCode() : "US";
        return ResponseEntity.ok(
                sessionService.createSession(
                        body.reference(), body.amount(), body.currency(),
                        body.shopperReference(), countryCode, body.recurringProcessingModel())
        );
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<JsonNode> getSessionResult(@PathVariable String id,
                                                     @RequestParam String sessionResult) {
        return ResponseEntity.ok(sessionService.getSessionResult(id, sessionResult));
    }

    @ExceptionHandler(AdyenApiException.class)
    public ResponseEntity<String> handleAdyen(AdyenApiException e) {
        return ResponseEntity.status(502).body(e.getResponseBody());
    }
}
