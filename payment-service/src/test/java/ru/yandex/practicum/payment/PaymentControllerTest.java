package ru.yandex.practicum.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * WebFlux slice test: boots only the web layer (controller + exception handler).
 * {@link BalanceStore} is mocked so tests are fully deterministic.
 *
 * <p>{@link SecurityConfig} is excluded from the slice — these tests focus on
 * controller behaviour, not security rules. Authentication/authorization is
 * exercised in dedicated tests (see Phase 6 of Sprint 8).
 */
@WebFluxTest(
    controllers = PaymentController.class,
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = SecurityConfig.class)
)
@Import(PaymentExceptionHandler.class)
class PaymentControllerTest {

    @Autowired
    WebTestClient client;

    @MockBean
    BalanceStore balanceStore;

    // -----------------------------------------------------------------------
    // GET /balance
    // -----------------------------------------------------------------------

    @Test
    void getBalance_returns200WithBalance() {
        when(balanceStore.getBalance()).thenReturn(75_000L);

        client.get().uri("/balance")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.balance").isEqualTo(75_000);
    }

    // -----------------------------------------------------------------------
    // POST /payment — success
    // -----------------------------------------------------------------------

    @Test
    void processPayment_success_returns200WithRemainingBalance() {
        when(balanceStore.deduct(5_000L)).thenReturn(95_000L);

        client.post().uri("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                        {"orderId": 42, "amount": 5000}
                        """)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.remainingBalance").isEqualTo(95_000);
    }

    @Test
    void processPayment_multipleItems_correctAmountPassedToStore() {
        when(balanceStore.deduct(12_300L)).thenReturn(87_700L);

        client.post().uri("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                        {"orderId": 7, "amount": 12300}
                        """)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.remainingBalance").isEqualTo(87_700);
    }

    // -----------------------------------------------------------------------
    // POST /payment — 402 insufficient funds
    // -----------------------------------------------------------------------

    @Test
    void processPayment_insufficientFunds_returns402WithPaymentErrorBody() {
        when(balanceStore.deduct(anyLong()))
            .thenThrow(new InsufficientFundsException(500L));

        client.post().uri("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                        {"orderId": 1, "amount": 99999}
                        """)
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.PAYMENT_REQUIRED)
            .expectBody()
            .jsonPath("$.message").isEqualTo("Insufficient funds")
            .jsonPath("$.balance").isEqualTo(500);
    }

    @Test
    void processPayment_insufficientFunds_balanceFieldMatchesCurrentBalance() {
        when(balanceStore.deduct(anyLong()))
            .thenThrow(new InsufficientFundsException(1_234L));

        client.post().uri("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                        {"orderId": 2, "amount": 50000}
                        """)
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.PAYMENT_REQUIRED)
            .expectBody()
            .jsonPath("$.balance").isEqualTo(1_234);
    }
}
