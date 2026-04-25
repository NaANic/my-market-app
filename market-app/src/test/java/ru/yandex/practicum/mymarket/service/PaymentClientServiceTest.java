package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.payment.client.api.PaymentApi;
import ru.yandex.practicum.payment.client.model.BalanceResponse;
import ru.yandex.practicum.payment.client.model.PaymentRequest;
import ru.yandex.practicum.payment.client.model.PaymentResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentClientServiceTest {

  @Mock
  PaymentApi paymentApi;

  @InjectMocks
  PaymentClientService paymentClientService;

  // -----------------------------------------------------------------------
  // getBalance
  // -----------------------------------------------------------------------

  @Test
  void getBalance_returnsBalanceFromApi() {
    // Both getBalance and processPayment live on the single generated PaymentApi.
    when(paymentApi.getBalance())
        .thenReturn(Mono.just(new BalanceResponse().balance(75_000L)));

    StepVerifier.create(paymentClientService.getBalance())
        .assertNext(balance -> assertThat(balance).isEqualTo(75_000L))
        .verifyComplete();

    verify(paymentApi).getBalance();
  }

  // -----------------------------------------------------------------------
  // pay — success
  // -----------------------------------------------------------------------

  @Test
  void pay_success_returnsRemainingBalance() {
    when(paymentApi.processPayment(any(PaymentRequest.class)))
        .thenReturn(Mono.just(new PaymentResponse()
            .success(true)
            .remainingBalance(95_000L)));

    StepVerifier.create(paymentClientService.pay(42L, 5_000L))
        .assertNext(remaining -> assertThat(remaining).isEqualTo(95_000L))
        .verifyComplete();

    verify(paymentApi).processPayment(any(PaymentRequest.class));
  }

  // -----------------------------------------------------------------------
  // pay — error propagation
  // -----------------------------------------------------------------------

  @Test
  void pay_propagatesErrorFromApi() {
    when(paymentApi.processPayment(any(PaymentRequest.class)))
        .thenReturn(Mono.error(new RuntimeException("network error")));

    StepVerifier.create(paymentClientService.pay(1L, 1_000L))
        .expectErrorMatches(ex -> ex instanceof RuntimeException
            && ex.getMessage().equals("network error"))
        .verify();
  }
}
