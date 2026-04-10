package ru.yandex.practicum.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@WebFluxTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    WebTestClient client;

    @MockBean
    BalanceStore balanceStore;

    @Test
    void getBalance_returnsCurrentBalance() {
        when(balanceStore.getBalance()).thenReturn(75_000L);

        client.get().uri("/balance")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.balance").isEqualTo(75_000);
    }

    @Test
    void processPayment_success_returns200WithRemainingBalance() {
        when(balanceStore.deduct(5_000L)).thenReturn(95_000L);

        client.post().uri("/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"orderId\": 1, \"amount\": 5000}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true)
                .jsonPath("$.remainingBalance").isEqualTo(95_000);
    }

    @Test
    void processPayment_insufficientFunds_returns402() {
        when(balanceStore.deduct(anyLong()))
                .thenThrow(new InsufficientFundsException(3_000L));

        client.post().uri("/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"orderId\": 2, \"amount\": 99999}")
                .exchange()
                .expectStatus().isEqualTo(402)
                .expectBody()
                .jsonPath("$.message").isEqualTo("Insufficient funds")
                .jsonPath("$.balance").isEqualTo(3_000);
    }
}
