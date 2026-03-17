package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.yandex.practicum.mymarket.entity.CartItem;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

  @EntityGraph(attributePaths = "item")
  List<CartItem> findBySessionId(String sessionId);

  Optional<CartItem> findBySessionIdAndItemId(String sessionId, Long itemId);

  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM CartItem c WHERE c.sessionId = :sid")
  void deleteBySessionId(@Param("sid") String sessionId);
}
