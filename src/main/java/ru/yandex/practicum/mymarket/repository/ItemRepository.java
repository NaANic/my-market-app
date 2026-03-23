package ru.yandex.practicum.mymarket.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.yandex.practicum.mymarket.entity.Item;

public interface ItemRepository extends JpaRepository<Item, Long> {

  @Query("""
           SELECT i FROM Item i
           WHERE LOWER(i.title) LIKE LOWER(CONCAT('%', :query, '%'))
              OR LOWER(i.description) LIKE LOWER(CONCAT('%', :query, '%'))
           """)
  Page<Item> search(@Param("query") String query, Pageable pageable);
}
