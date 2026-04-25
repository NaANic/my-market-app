package ru.yandex.practicum.mymarket.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.config.R2dbcConfig;
import ru.yandex.practicum.mymarket.config.TestDataR2dbcConfig;
import ru.yandex.practicum.mymarket.entity.Item;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataR2dbcTest
@Import({R2dbcConfig.class, TestDataR2dbcConfig.class})
@ActiveProfiles("test")
class ItemRepositoryTest {

  @Autowired
  ItemRepository itemRepository;

  @BeforeEach
  void setUp() {
    itemRepository.deleteAll()
        .then(itemRepository.saveAll(List.of(
            new Item("Футбольный мяч", "Профессиональный мяч для футбола", "/img/ball.jpg", 2500),
            new Item("Баскетбольный мяч", "Мяч для баскетбола", "/img/bball.jpg", 3000),
            new Item("Кроссовки беговые", "Лёгкие кроссовки для бега", "/img/snkrs.jpg", 7900)
        )).then())
        .block();
  }

  @Test
  void searchBy_byTitle_findsBothBalls() {
    StepVerifier.create(itemRepository.searchBy("мяч", PageRequest.of(0, 10)).collectList())
        .assertNext(items -> assertThat(items).hasSize(2))
        .verifyComplete();
  }

  @Test
  void searchBy_byDescription_findsOne() {
    StepVerifier.create(itemRepository.searchBy("баскетбол", PageRequest.of(0, 10)).collectList())
        .assertNext(items -> {
          assertThat(items).hasSize(1);
          assertThat(items.get(0).getTitle()).isEqualTo("Баскетбольный мяч");
        })
        .verifyComplete();
  }

  @Test
  void searchBy_caseInsensitive() {
    StepVerifier.create(itemRepository.searchBy("КРОССОВКИ", PageRequest.of(0, 10)).collectList())
        .assertNext(items -> assertThat(items).hasSize(1))
        .verifyComplete();
  }

  @Test
  void searchBy_noMatch_returnsEmpty() {
    StepVerifier.create(itemRepository.searchBy("zzzzz", PageRequest.of(0, 10)).collectList())
        .assertNext(items -> assertThat(items).isEmpty())
        .verifyComplete();
  }

  @Test
  void countBySearch_returnsCorrectCount() {
    StepVerifier.create(itemRepository.countBySearch("мяч"))
        .assertNext(count -> assertThat(count).isEqualTo(2))
        .verifyComplete();
  }

  @Test
  void findAllBy_sortByPrice_ascending() {
    StepVerifier.create(itemRepository.findAllBy(
            PageRequest.of(0, 10, Sort.by("price"))).collectList())
        .assertNext(items -> {
          assertThat(items.get(0).getPrice()).isEqualTo(2500);
          assertThat(items.get(2).getPrice()).isEqualTo(7900);
        })
        .verifyComplete();
  }

  @Test
  void findAllBy_sortByTitle_alphabetical() {
    StepVerifier.create(itemRepository.findAllBy(
            PageRequest.of(0, 10, Sort.by("title"))).collectList())
        .assertNext(items -> {
          List<String> titles = items.stream().map(Item::getTitle).toList();
          assertThat(titles).isSorted();
        })
        .verifyComplete();
  }

  @Test
  void findAllBy_pagination_respectsPageSize() {
    StepVerifier.create(itemRepository.findAllBy(PageRequest.of(0, 2)).collectList())
        .assertNext(items -> assertThat(items).hasSize(2))
        .verifyComplete();
  }
}
