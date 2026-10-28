package com.example.adyen.service;

import com.example.adyen.client.AdyenClient;
import com.example.adyen.config.AdyenEnvironment;
import com.example.adyen.dto.SessionRequest;
import com.example.adyen.dto.SessionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionServiceTest {

    private AdyenClient client;
    private SessionService service;

    @BeforeEach
    void setUp() {
        client = mock(AdyenClient.class);
        AdyenEnvironment env = mock(AdyenEnvironment.class);
        when(env.merchantAccount()).thenReturn("TestMerchant");
        when(env.returnUrl()).thenReturn("http://localhost:8080/return");
        when(client.createSession(any())).thenReturn(new SessionResponse("S1", "DATA", null, null, null, null, null));
        service = new SessionService(client, env);
    }

    private SessionRequest capture() {
        ArgumentCaptor<SessionRequest> c = ArgumentCaptor.forClass(SessionRequest.class);
        verify(client).createSession(c.capture());
        return c.getValue();
    }

    @Test
    void zeroAmount_setsEcommerceStoreAndDefaultRpm() {
        service.createSession("R1", 0L, "USD", "shopper-1", "US", null);
        SessionRequest r = capture();
        assertThat(r.amount().value()).isZero();
        assertThat(r.shopperInteraction()).isEqualTo("Ecommerce");
        assertThat(r.storePaymentMethod()).isTrue();
        assertThat(r.recurringProcessingModel()).isEqualTo("CardOnFile");
        assertThat(r.shopperReference()).isEqualTo("shopper-1");
    }

    @Test
    void positiveAmount_andExplicitRpm() {
        service.createSession("R2", 1500L, "USD", "shopper-1", "US", "Subscription");
        SessionRequest r = capture();
        assertThat(r.amount().value()).isEqualTo(1500L);
        assertThat(r.recurringProcessingModel()).isEqualTo("Subscription");
        assertThat(r.shopperInteraction()).isEqualTo("Ecommerce");
    }

    @Test
    void blankRpm_fallsBackToCardOnFile() {
        service.createSession("R3", 0L, "USD", "s", "US", " ");
        assertThat(capture().recurringProcessingModel()).isEqualTo("CardOnFile");
    }
}
