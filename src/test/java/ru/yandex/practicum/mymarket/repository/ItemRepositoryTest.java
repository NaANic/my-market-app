package ru.yandex.practicum.mymarket.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import ru.yandex.practicum.mymarket.entity.Item;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
class ItemRepositoryTest {

  @Autowired
  ItemRepository itemRepository;

  @BeforeEach
  void setUp() {
    itemRepository.deleteAll();
    itemRepository.saveAll(List.of(
        new Item("Футбольный мяч", "Профессиональный мяч для футбола", "/img/ball.jpg", 2500),
        new Item("Баскетбольный мяч", "Мяч для баскетбола", "/img/bball.jpg", 3000),
        new Item("Кроссовки беговые", "Лёгкие кроссовки для бега", "/img/snkrs.jpg", 7900)
    ));
  }

  @Test
  void search_byTitle_findsBothBalls() {
    Page<Item> result = itemRepository.search("мяч", PageRequest.of(0, 10));
    assertThat(result.getContent()).hasSize(2);
  }

  @Test
  void search_byDescription_findsOne() {
    Page<Item> result = itemRepository.search("баскетбол", PageRequest.of(0, 10));
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getContent().get(0).getTitle()).isEqualTo("Баскетбольный мяч");
  }

  @Test
  void search_caseInsensitive() {
    Page<Item> result = itemRepository.search("КРОССОВКИ", PageRequest.of(0, 10));
    assertThat(result.getContent()).hasSize(1);
  }

  @Test
  void search_withPagination_respectsPageSize() {
    Page<Item> result = itemRepository.search("мяч", PageRequest.of(0, 1));
    assertThat(result.getContent()).hasSize(1);
    assertThat(result.getTotalElements()).isEqualTo(2);
    assertThat(result.hasNext()).isTrue();
  }

  @Test
  void search_noMatch_returnsEmpty() {
    Page<Item> result = itemRepository.search("zzzzz", PageRequest.of(0, 10));
    assertThat(result.getContent()).isEmpty();
  }

  @Test
  void findAll_sortByPrice_ascending() {
    Page<Item> result = itemRepository.findAll(PageRequest.of(0, 10, Sort.by("price")));
    List<Item> items = result.getContent();
    assertThat(items.get(0).getPrice()).isEqualTo(2500);
    assertThat(items.get(2).getPrice()).isEqualTo(7900);
  }

  @Test
  void findAll_sortByTitle_alphabetical() {
    Page<Item> result = itemRepository.findAll(PageRequest.of(0, 10, Sort.by("title")));
    List<String> titles = result.getContent().stream().map(Item::getTitle).toList();
    assertThat(titles).isSorted();
  }
}
