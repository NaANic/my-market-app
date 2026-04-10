package ru.yandex.practicum.mymarket.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import ru.yandex.practicum.mymarket.exception.CartIsEmptyException;
import ru.yandex.practicum.mymarket.exception.EntityNotFoundException;

import java.net.URI;

@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * 404 – entity (Item, Order, …) was not found.
   * Replaces scattered {@code RuntimeException("Товар не найден: …")}.
   */
  @ExceptionHandler(EntityNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ProblemDetail handleEntityNotFound(EntityNotFoundException ex,
                                            ServerWebExchange exchange) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    problem.setTitle("Сущность не найдена");
    problem.setType(URI.create("https://example.com/errors/entity-not-found"));
    problem.setProperty("entityName", ex.getEntityName());
    problem.setProperty("entityId",   ex.getEntityId());
    problem.setInstance(URI.create(exchange.getRequest().getPath().value()));
    return problem;
  }

  /**
   * 422 – cannot process the request because the cart is empty (e.g. checkout).
   *
   * We use 422 Unprocessable Entity rather than 200 because the POST /buy
   * request genuinely failed — an empty cart is a client-side precondition
   * that was not met. 200 would be misleading to API consumers and monitoring.
   *
   * If your frontend expects 200 + empty list (e.g. GET /cart), handle the
   * empty-cart display case in the controller directly (return Flux.empty())
   * rather than throwing this exception.
   */
  @ExceptionHandler(CartIsEmptyException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  public ProblemDetail handleCartIsEmpty(CartIsEmptyException ex,
                                         ServerWebExchange exchange) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    problem.setTitle("Корзина пуста");
    problem.setType(URI.create("https://example.com/errors/cart-empty"));
    problem.setInstance(URI.create(exchange.getRequest().getPath().value()));
    return problem;
  }
}
