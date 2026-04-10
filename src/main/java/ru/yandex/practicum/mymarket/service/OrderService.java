package ru.yandex.practicum.mymarket.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.entity.CustomerOrder;
import ru.yandex.practicum.mymarket.entity.OrderItem;
import ru.yandex.practicum.mymarket.exception.CartIsEmptyException;
import ru.yandex.practicum.mymarket.exception.EntityNotFoundException;
import ru.yandex.practicum.mymarket.repository.OrderItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

@Service
public class OrderService {

  private final OrderRepository orderRepository;
  private final OrderItemRepository orderItemRepository;
  private final CartService cartService;

  public OrderService(OrderRepository orderRepository,
      OrderItemRepository orderItemRepository,
      CartService cartService) {
    this.orderRepository = orderRepository;
    this.orderItemRepository = orderItemRepository;
    this.cartService = cartService;
  }

  public Mono<Long> createOrder(String sessionId) {
    // Enrich cart with item data (price, title) in one pass, then save order + order items
    return cartService.getCartItemDtos(sessionId)
        .collectList()
        .flatMap(dtos -> {
          if (dtos.isEmpty()) {
            return Mono.error(new CartIsEmptyException(sessionId));
          }
          long totalSum = dtos.stream()
              .mapToLong(dto -> dto.price() * dto.count())
              .sum();

          return orderRepository.save(new CustomerOrder(sessionId, totalSum))
              .flatMap(order -> orderItemRepository.saveAll(
                      Flux.fromIterable(dtos)
                          .map(dto -> new OrderItem(
                              order.getId(),
                              dto.id(),
                              dto.title(),
                              dto.price(),
                              dto.count()
                          ))
                  )
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
}
