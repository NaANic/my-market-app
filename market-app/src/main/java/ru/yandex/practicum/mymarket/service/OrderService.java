package ru.yandex.practicum.mymarket.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.entity.CustomerOrder;
import ru.yandex.practicum.mymarket.entity.OrderItem;
import ru.yandex.practicum.mymarket.exception.CartIsEmptyException;
import ru.yandex.practicum.mymarket.exception.EntityNotFoundException;
import ru.yandex.practicum.mymarket.exception.PaymentFailedException;
import ru.yandex.practicum.mymarket.repository.OrderItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

import java.util.List;

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
   *   <li>Charge the payment-service — maps HTTP 402 to
   *       {@link PaymentFailedException} so the caller surfaces a
   *       meaningful error to the user.</li>
   *   <li>Persist order items and clear the cart (only on payment success).</li>
   * </ol>
   */
  public Mono<Long> createOrder(Long userId) {
    return cartService.getCartItemDtos(userId)
        .collectList()
        .flatMap(dtos -> {
          if (dtos.isEmpty()) {
            return Mono.error(new CartIsEmptyException(String.valueOf(userId)));
          }

          long totalSum = dtos.stream()
              .mapToLong(dto -> dto.price() * dto.count())
              .sum();

          return orderRepository.save(new CustomerOrder(userId, totalSum))
              .flatMap(order ->
                  paymentClientService.pay(order.getId(), totalSum)
                      .onErrorMap(
                          ex -> ex instanceof WebClientResponseException wex
                              && wex.getStatusCode() == HttpStatus.PAYMENT_REQUIRED,
                          ex -> new PaymentFailedException(
                              order.getId(),
                              extractBalance((WebClientResponseException) ex)))
                      .then(Mono.defer(() -> saveOrderItems(order.getId(), dtos)))
                      .then(Mono.defer(() -> cartService.clearCart(userId)))
                      .thenReturn(order.getId())
              );
        });
  }

  public Flux<OrderDto> getOrders(Long userId) {
    return orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
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

  private Mono<Void> saveOrderItems(long orderId, List<ItemDto> dtos) {
    List<OrderItem> items = dtos.stream()
        .map(dto -> new OrderItem(orderId, dto.id(), dto.title(), dto.price(), dto.count()))
        .toList();
    return orderItemRepository.saveAll(items).then();
  }

  /**
   * Extracts the remaining balance from a 402 response body.
   * Payment-service returns {@code {"message":"...","balance":<long>}}.
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
