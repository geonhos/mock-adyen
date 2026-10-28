package com.example.adyen.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionResponse(
        String id,
        String sessionData,
        String returnUrl,
        String reference,
        String countryCode,
        Amount amount,
        String merchantAccount
) {
}
