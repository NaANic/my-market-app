package ru.yandex.practicum.mymarket.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.exception.CartIsEmptyException;
import ru.yandex.practicum.mymarket.exception.EntityNotFoundException;
import ru.yandex.practicum.mymarket.exception.PaymentFailedException;

import java.net.URI;

@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * 404 – entity (Item, Order, …) was not found.
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

  /**
   * Payment failure (HTTP 402 from the payment-service).
   *
   * <p>Because this is a server-rendered Thymeleaf application, returning a
   * JSON {@link ProblemDetail} would display raw JSON in the browser — a poor
   * user experience. Instead we redirect to the cart page with an {@code error}
   * query parameter that {@code cart.html} renders as a dismissible alert.
   *
   * <p>The redirect is a 302 so the browser replaces the failed POST /buy with
   * a GET /cart/items, preventing a re-submission on refresh.
   */
  @ExceptionHandler(PaymentFailedException.class)
  public Mono<Void> handlePaymentFailed(PaymentFailedException ex,
      ServerWebExchange exchange) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.FOUND);

    String redirectUrl = UriComponentsBuilder.fromPath("/cart/items")
        .queryParam("error", ex.getMessage())
        .build()
        .toUriString();

    response.getHeaders().setLocation(URI.create(redirectUrl));
    return response.setComplete();
  }
}
