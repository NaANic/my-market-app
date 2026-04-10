package ru.yandex.practicum.mymarket.exception;

/** Thrown when an operation (e.g. checkout) requires a non-empty cart. Maps to HTTP 422. */
public class CartIsEmptyException extends AppException {

  private final String sessionId;

  public CartIsEmptyException(String sessionId) {
    super("Корзина пуста");
    this.sessionId = sessionId;
  }

  public String getSessionId() { return sessionId; }
}
