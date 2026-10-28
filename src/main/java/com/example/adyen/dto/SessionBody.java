package com.example.adyen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record SessionBody(
        @NotBlank String reference,
        @NotNull @PositiveOrZero Long amount,
        @NotBlank String currency,
        @NotBlank String shopperReference,
        String countryCode,
        // 선택. 기본 CardOnFile.
        String recurringProcessingModel
) {
}
