package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.entity.User;

public interface UserRepository extends ReactiveCrudRepository<User, Long> {
  Mono<User> findByUsername(String username);
}
