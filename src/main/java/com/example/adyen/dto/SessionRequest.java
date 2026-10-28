package com.example.adyen.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SessionRequest(
        Amount amount,
        String reference,
        String returnUrl,
        String merchantAccount,
        String countryCode,
        String shopperReference,
        String channel,
        Boolean storePaymentMethod,
        // 토큰 저장(storePaymentMethod=true) 시 필수: 저장 토큰의 후속 용도.
        // CardOnFile = 고객이 직접 개시하는 후속 결제(고객 주도). 그 외 Subscription / UnscheduledCardOnFile.
        String recurringProcessingModel,
        // Ecommerce 명시 (지갑 저장 시 고객 개시 결제임을 Adyen에 알림)
        String shopperInteraction
) {
}
