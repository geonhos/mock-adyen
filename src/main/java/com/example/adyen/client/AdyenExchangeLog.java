package com.example.adyen.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adyen과 실제로 주고받은 HTTP 교환을 최근 N건 보관하는 링 버퍼.
 *
 * <p>테스트 콘솔 인스펙터가 "브라우저 ↔ 우리 서버"가 아니라 <b>"우리 서버 ↔ Adyen"</b> 구간을
 * 볼 수 있게 하려고 둔다. {@link AdyenExchangeInterceptor} 가 기록하고
 * {@code GET /api/debug/adyen-exchanges} 가 읽어간다.
 *
 * <p>메모리에만 있고 영속화하지 않는다. 재시작하면 사라진다.
 */
@Component
public class AdyenExchangeLog {

    /** 보관 건수. 인스펙터가 최근 것만 보므로 크게 잡을 이유가 없다. */
    private static final int CAPACITY = 50;

    private final ObjectMapper mapper;
    private final Deque<Exchange> buffer = new ArrayDeque<>(CAPACITY);
    private final AtomicLong sequence = new AtomicLong();

    public AdyenExchangeLog(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public synchronized void record(String method,
                                    String uri,
                                    Map<String, String> requestHeaders,
                                    String requestBody,
                                    Integer status,
                                    Map<String, String> responseHeaders,
                                    String responseBody,
                                    long durationMs,
                                    String error) {
        if (buffer.size() >= CAPACITY) {
            buffer.removeFirst();
        }
        buffer.addLast(new Exchange(
                sequence.incrementAndGet(),
                System.currentTimeMillis(),
                method,
                uri,
                requestHeaders,
                asJsonIfPossible(requestBody),
                status,
                responseHeaders,
                asJsonIfPossible(responseBody),
                durationMs,
                error));
    }

    /** {@code afterSeq} 보다 큰 시퀀스의 교환만 오래된 순으로 반환한다. */
    public synchronized List<Exchange> after(long afterSeq) {
        List<Exchange> out = new ArrayList<>();
        for (Exchange e : buffer) {
            if (e.seq() > afterSeq) {
                out.add(e);
            }
        }
        return out;
    }

    /**
     * 본문이 JSON이면 파싱해서 넣는다(인스펙터에서 예쁘게 펼쳐 보이도록).
     * JSON이 아니거나 비었으면 원문 문자열/null 그대로.
     */
    private Object asJsonIfPossible(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode node = mapper.readTree(raw);
            return node.isMissingNode() ? raw : node;
        } catch (Exception e) {
            return raw;
        }
    }

    /**
     * Adyen과의 HTTP 교환 1건.
     *
     * @param seq         단조 증가 시퀀스. 프론트가 커서로 쓴다
     * @param error       전송 자체가 실패한 경우의 메시지. 정상 응답이면 null
     */
    public record Exchange(
            long seq,
            long timestamp,
            String method,
            String uri,
            Map<String, String> requestHeaders,
            Object requestBody,
            Integer status,
            Map<String, String> responseHeaders,
            Object responseBody,
            long durationMs,
            String error
    ) {
    }
}
