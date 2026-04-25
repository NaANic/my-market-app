package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import ru.yandex.practicum.mymarket.entity.OrderItem;

public interface OrderItemRepository extends ReactiveCrudRepository<OrderItem, Long> {

  Flux<OrderItem> findByOrderId(Long orderId);
}
