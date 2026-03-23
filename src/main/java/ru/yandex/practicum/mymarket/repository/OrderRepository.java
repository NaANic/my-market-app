package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.yandex.practicum.mymarket.entity.CustomerOrder;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {

  @EntityGraph(attributePaths = "items")
  List<CustomerOrder> findBySessionIdOrderByCreatedAtDesc(String sessionId);

  @Override
  @EntityGraph(attributePaths = "items")
  Optional<CustomerOrder> findById(Long id);
}
