package ru.yandex.practicum.mymarket.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.entity.CustomerOrder;
import ru.yandex.practicum.mymarket.entity.OrderItem;
import ru.yandex.practicum.mymarket.exception.CartIsEmptyException;
import ru.yandex.practicum.mymarket.exception.EntityNotFoundException;
import ru.yandex.practicum.mymarket.exception.PaymentFailedException;
import ru.yandex.practicum.mymarket.repository.OrderItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

@Service
public class OrderService {

  private final OrderRepository      orderRepository;
  private final OrderItemRepository  orderItemRepository;
  private final CartService          cartService;
  private final PaymentClientService paymentClientService;

  public OrderService(OrderRepository orderRepository,
      OrderItemRepository orderItemRepository,
      CartService cartService,
      PaymentClientService paymentClientService) {
    this.orderRepository     = orderRepository;
    this.orderItemRepository = orderItemRepository;
    this.cartService         = cartService;
    this.paymentClientService = paymentClientService;
  }

  /**
   * Full checkout sequence:
   * <ol>
   *   <li>Collect cart items (error if empty).</li>
   *   <li>Persist the order to obtain its database ID.</li>
   *   <li>Charge the payment-service — maps HTTP 402 to
   *       {@link PaymentFailedException} so the caller can surface a
   *       meaningful error to the user.</li>
   *   <li>Persist order items and clear the cart (only on payment success).</li>
   * </ol>
   *
   * <p>The persisted {@link CustomerOrder} row is intentionally kept on
   * payment failure as an audit record. A {@code status} column can be added
   * in a future sprint to distinguish {@code PAID} from {@code FAILED}.
   */
  public Mono<Long> createOrder(String sessionId) {
    return cartService.getCartItemDtos(sessionId)
        .collectList()
        .flatMap(dtos -> {
          if (dtos.isEmpty()) {
            return Mono.error(new CartIsEmptyException(sessionId));
          }

          long totalSum = dtos.stream()
              .mapToLong(dto -> dto.price() * dto.count())
              .sum();

          // Step 1: persist order to obtain its generated ID
          return orderRepository.save(new CustomerOrder(sessionId, totalSum))
              .flatMap(order ->
                  // Step 2: charge the payment-service
                  paymentClientService.pay(order.getId(), totalSum)
                      .onErrorMap(
                          // WebClientResponseException.PaymentRequired does not
                          // exist in the webclient generator runtime — match on
                          // the status code instead.
                          ex -> ex instanceof WebClientResponseException wex
                              && wex.getStatusCode() == HttpStatus.PAYMENT_REQUIRED,
                          ex -> new PaymentFailedException(
                              order.getId(),
                              extractBalance((WebClientResponseException) ex)))
                      // Step 3: persist order items (only on payment success)
                      .then(orderItemRepository.saveAll(
                          Flux.fromIterable(dtos)
                              .map(dto -> new OrderItem(
                                  order.getId(),
                                  dto.id(),
                                  dto.title(),
                                  dto.price(),
                                  dto.count()))
                      ).then())
                      // Step 4: clear the cart
                      .then(cartService.clearCart(sessionId))
                      .thenReturn(order.getId())
              );
        });
  }

  public Flux<OrderDto> getOrders(String sessionId) {
    return orderRepository.findBySessionIdOrderByCreatedAtDesc(sessionId)
        .flatMap(order -> orderItemRepository.findByOrderId(order.getId())
            .collectList()
            .map(items -> OrderDto.of(order, items)));
  }

  public Mono<OrderDto> getOrder(long id) {
    return orderRepository.findById(id)
        .switchIfEmpty(Mono.error(new EntityNotFoundException("Заказ", id)))
        .flatMap(order -> orderItemRepository.findByOrderId(order.getId())
            .collectList()
            .map(items -> OrderDto.of(order, items)));
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  /**
   * Attempts to extract the remaining balance from the 402 response body.
   * The payment-service returns {@code {"message":"...","balance":<long>}}.
   * Falls back to {@code -1} if the body is absent or cannot be parsed.
   */
  private long extractBalance(WebClientResponseException ex) {
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper =
          new com.fasterxml.jackson.databind.ObjectMapper();
      com.fasterxml.jackson.databind.JsonNode root =
          mapper.readTree(ex.getResponseBodyAsString());
      return root.path("balance").asLong(-1L);
    } catch (Exception ignored) {
      return -1L;
    }
  }
}
