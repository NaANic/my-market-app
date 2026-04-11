package ru.yandex.practicum.mymarket.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.payment.client.api.BalanceApi;
import ru.yandex.practicum.payment.client.api.PaymentApi;
import ru.yandex.practicum.payment.client.model.PaymentRequest;

/**
 * Thin façade over the generated OpenAPI WebClient stubs.
 *
 * <p>Keeps all generated-API types (model classes, {@code ApiClient}) out of
 * business-logic classes such as {@link OrderService}, so that:
 * <ul>
 *   <li>Unit tests can mock this service without touching the generated code.</li>
 *   <li>If the OpenAPI spec changes, only this class and its config need updating.</li>
 * </ul>
 *
 * <p>Error handling strategy: the generated WebClient stub maps non-2xx HTTP
 * responses to {@link org.springframework.web.reactive.function.client.WebClientResponseException}
 * subclasses (e.g. {@code WebClientResponseException.PaymentRequired} for 402).
 * Callers are responsible for converting those into domain exceptions — see
 * {@link OrderService} for the 402 → {@code PaymentFailedException} mapping.
 */
@Service
public class PaymentClientService {

  private final PaymentApi paymentApi;
  private final BalanceApi balanceApi;

  public PaymentClientService(PaymentApi paymentApi, BalanceApi balanceApi) {
    this.paymentApi = paymentApi;
    this.balanceApi = balanceApi;
  }

  /**
   * Returns the current account balance in kopecks.
   */
  public Mono<Long> getBalance() {
    return balanceApi.getBalance()
        .map(response -> response.getBalance());
  }

  /**
   * Deducts {@code amount} kopecks from the account for the given order.
   *
   * @param orderId the persisted order ID (used as the payment reference)
   * @param amount  total in kopecks to charge
   * @return remaining balance in kopecks after a successful deduction
   * @throws org.springframework.web.reactive.function.client.WebClientResponseException.PaymentRequired
   *         (HTTP 402) if the account has insufficient funds
   */
  public Mono<Long> pay(long orderId, long amount) {
    PaymentRequest req = new PaymentRequest()
        .orderId(orderId)
        .amount(amount);
    return paymentApi.processPayment(req)
        .map(response -> response.getRemainingBalance());
  }
}
