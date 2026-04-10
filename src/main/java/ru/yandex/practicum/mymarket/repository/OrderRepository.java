package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import ru.yandex.practicum.mymarket.entity.CustomerOrder;

public interface OrderRepository extends ReactiveCrudRepository<CustomerOrder, Long> {

  // findById(Long) returning Mono<CustomerOrder> is inherited from ReactiveCrudRepository
  Flux<CustomerOrder> findBySessionIdOrderByCreatedAtDesc(String sessionId);
}
