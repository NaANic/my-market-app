package ru.yandex.practicum.mymarket.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.payment.client.api.PaymentApi;
import ru.yandex.practicum.payment.client.model.PaymentRequest;

/**
 * Thin façade over the generated OpenAPI WebClient stub.
 *
 * <p>The {@code java/webclient} generator merges all operations sharing the
 * same tag into one class. Both {@code getBalance} and {@code processPayment}
 * therefore live in {@link PaymentApi} — there is no separate {@code BalanceApi}.
 *
 * <p>Keeps all generated-API types out of business-logic classes such as
 * {@link OrderService}, so that unit tests can mock this service without
 * touching the generated code, and spec changes only require updating this
 * class and its config.
 *
 * <p>Error handling: the generated WebClient stub surfaces non-2xx responses
 * as {@link org.springframework.web.reactive.function.client.WebClientResponseException}.
 * Callers map the 402 status to a domain exception — see {@link OrderService}.
 */
@Service
public class PaymentClientService {

  private final PaymentApi paymentApi;

  public PaymentClientService(PaymentApi paymentApi) {
    this.paymentApi = paymentApi;
  }

  /**
   * Returns the current account balance in kopecks.
   */
  public Mono<Long> getBalance() {
    return paymentApi.getBalance()
        .map(response -> response.getBalance());
  }

  /**
   * Deducts {@code amount} kopecks from the account for the given order.
   *
   * @param orderId the persisted order ID (used as the payment reference)
   * @param amount  total in kopecks to charge
   * @return remaining balance in kopecks after a successful deduction
   * @throws org.springframework.web.reactive.function.client.WebClientResponseException
   *         with status 402 if the account has insufficient funds
   */
  public Mono<Long> pay(long orderId, long amount) {
    PaymentRequest req = new PaymentRequest()
        .orderId(orderId)
        .amount(amount);
    return paymentApi.processPayment(req)
        .map(response -> response.getRemainingBalance());
  }
}
