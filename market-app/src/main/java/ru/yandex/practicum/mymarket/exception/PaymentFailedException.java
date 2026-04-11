package ru.yandex.practicum.mymarket.exception;

/**
 * Thrown when the payment-service rejects a charge (HTTP 402 — insufficient funds).
 * Maps to HTTP 402 in {@link ru.yandex.practicum.mymarket.web.GlobalExceptionHandler}.
 */
public class PaymentFailedException extends AppException {

  private final long orderId;
  private final long currentBalance;

  public PaymentFailedException(long orderId, long currentBalance) {
    super("Недостаточно средств для оплаты заказа №" + orderId
        + ". Баланс: " + currentBalance + " коп.");
    this.orderId        = orderId;
    this.currentBalance = currentBalance;
  }

  public long getOrderId()        { return orderId; }
  public long getCurrentBalance() { return currentBalance; }
}
