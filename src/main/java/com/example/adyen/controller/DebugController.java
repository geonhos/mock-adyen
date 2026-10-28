package com.example.adyen.controller;

import com.example.adyen.client.AdyenExchangeLog;
import com.example.adyen.client.AdyenExchangeLog.Exchange;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 테스트 콘솔 전용 — 서버가 Adyen과 실제로 주고받은 HTTP 교환 조회.
 *
 * <p>인스펙터가 "브라우저 ↔ 우리 서버"에 더해 <b>"우리 서버 ↔ Adyen"</b> 구간까지 보여주기 위한 경로다.
 * API 키 등 민감 헤더는 {@code AdyenExchangeInterceptor} 에서 마스킹된 상태로 저장된다.
 *
 * <p><b>운영에 노출하지 말 것.</b> 요청/응답 본문이 그대로 담긴다.
 */
@RestController
@RequestMapping("/api/debug")
public class DebugController {

    private final AdyenExchangeLog exchangeLog;

    public DebugController(AdyenExchangeLog exchangeLog) {
        this.exchangeLog = exchangeLog;
    }

    /**
     * @param afterSeq 이 시퀀스보다 큰 교환만 반환. 프론트가 마지막으로 본 seq를 커서로 넘긴다
     *                 (브라우저/서버 시계 차이에 영향받지 않게 시간 대신 시퀀스를 쓴다)
     */
    @GetMapping("/adyen-exchanges")
    public List<Exchange> adyenExchanges(@RequestParam(defaultValue = "0") long afterSeq) {
        return exchangeLog.after(afterSeq);
    }
}
