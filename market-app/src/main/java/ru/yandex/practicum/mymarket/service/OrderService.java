package ru.yandex.practicum.mymarket.service;

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

  private final OrderRepository        orderRepository;
  private final OrderItemRepository    orderItemRepository;
  private final CartService            cartService;
  private final PaymentClientService   paymentClientService;

  public OrderService(OrderRepository orderRepository,
      OrderItemRepository orderItemRepository,
      CartService cartService,
      PaymentClientService paymentClientService) {
    this.orderRepository      = orderRepository;
    this.orderItemRepository  = orderItemRepository;
    this.cartService          = cartService;
    this.paymentClientService = paymentClientService;
  }

  /**
   * Full checkout sequence:
   * <ol>
   *   <li>Collect cart items (error if empty).</li>
   *   <li>Persist the order to obtain its database ID.</li>
   *   <li>Charge the payment-service — rolls back to {@link PaymentFailedException}
   *       on HTTP 402 so the caller can surface a meaningful error to the user.</li>
   *   <li>Persist order items and clear the cart (only reached on payment success).</li>
   * </ol>
   *
   * <p>Note: this implementation does not perform a compensating delete of the
   * persisted {@link CustomerOrder} on payment failure. The order row acts as an
   * audit record of the attempt; you may add a status column and mark it FAILED
   * in a future sprint if required.
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

          // Step 1: persist order to get its ID
          return orderRepository.save(new CustomerOrder(sessionId, totalSum))
              .flatMap(order ->
                  // Step 2: charge the payment-service
                  paymentClientService.pay(order.getId(), totalSum)
                      .onErrorMap(
                          WebClientResponseException.PaymentRequired.class,
                          ex -> new PaymentFailedException(order.getId(), extractBalance(ex)))
                      // Step 3: save order items and clear the cart
                      .then(orderItemRepository.saveAll(
                          Flux.fromIterable(dtos)
                              .map(dto -> new OrderItem(
                                  order.getId(),
                                  dto.id(),
                                  dto.title(),
                                  dto.price(),
                                  dto.count()))
                      ).then())
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

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  /**
   * Attempts to extract the remaining balance from the 402 response body.
   * The payment-service returns {@code {"message":"...","balance":<long>}}.
   * Falls back to {@code -1} if parsing fails (e.g. network garbling).
   */
  private long extractBalance(WebClientResponseException.PaymentRequired ex) {
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
