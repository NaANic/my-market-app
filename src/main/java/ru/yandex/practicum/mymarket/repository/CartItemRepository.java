package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.entity.CartItem;

public interface CartItemRepository extends ReactiveCrudRepository<CartItem, Long> {

  Flux<CartItem> findBySessionId(String sessionId);

  Mono<CartItem> findBySessionIdAndItemId(String sessionId, Long itemId);

  // Derived delete — returns Mono<Void>, no @Modifying needed in R2DBC
  Mono<Void> deleteBySessionId(String sessionId);
}
