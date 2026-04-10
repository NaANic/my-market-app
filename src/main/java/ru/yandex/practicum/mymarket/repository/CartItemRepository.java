package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartItemRow;
import ru.yandex.practicum.mymarket.entity.CartItem;

public interface CartItemRepository extends ReactiveCrudRepository<CartItem, Long> {

  Flux<CartItem> findBySessionId(String sessionId);

  Mono<CartItem> findBySessionIdAndItemId(String sessionId, Long itemId);

  // Derived delete — returns Mono<Void>, no @Modifying needed in R2DBC
  Mono<Void> deleteBySessionId(String sessionId);

  /**
   * One-query JOIN alternative: fetches cart rows AND their item data in a
   * single roundtrip, returning a flat {@link CartItemRow} projection.
   *
   * <p>Use this instead of {@link #findBySessionId} + per-item
   * {@code itemRepository.findById} to eliminate N+1 at the SQL level.
   * CartService currently uses the two-query IN approach
   * ({@code findBySessionId} + {@code ItemRepository#findAllByIdIn}), which is
   * simpler to maintain. Switch to this method if you need maximum throughput
   * and a single DB roundtrip matters.
   */
  @Query("""
      SELECT ci.id            AS cart_item_id,
             ci.session_id,
             ci.count,
             i.id             AS item_id,
             i.title          AS item_title,
             i.description    AS item_description,
             i.img_path       AS item_img_path,
             i.price          AS item_price
      FROM   cart_items ci
      JOIN   items       i ON ci.item_id = i.id
      WHERE  ci.session_id = :sessionId
      """)
  Flux<CartItemRow> findWithItemsBySessionId(@Param("sessionId") String sessionId);
}
