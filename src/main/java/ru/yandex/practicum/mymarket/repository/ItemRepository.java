package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.entity.Item;

public interface ItemRepository extends R2dbcRepository<Item, Long> {

  // Derived query — Spring Data R2DBC applies sort + limit + offset from Pageable
  Flux<Item> findAllBy(Pageable pageable);

  @Query("""
           SELECT * FROM items
           WHERE LOWER(title) LIKE LOWER(CONCAT('%', :query, '%'))
              OR LOWER(description) LIKE LOWER(CONCAT('%', :query, '%'))
           """)
  Flux<Item> searchBy(String query, Pageable pageable);

  @Query("""
           SELECT COUNT(*) FROM items
           WHERE LOWER(title) LIKE LOWER(CONCAT('%', :query, '%'))
              OR LOWER(description) LIKE LOWER(CONCAT('%', :query, '%'))
           """)
  Mono<Long> countBySearch(String query);
}
