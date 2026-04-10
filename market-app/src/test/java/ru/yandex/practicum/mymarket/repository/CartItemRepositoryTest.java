package ru.yandex.practicum.mymarket.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.config.R2dbcConfig;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Item;

import static org.assertj.core.api.Assertions.assertThat;

@DataR2dbcTest
@Import(R2dbcConfig.class)
@ActiveProfiles("test")
class CartItemRepositoryTest {

  @Autowired
  CartItemRepository cartItemRepository;

  @Autowired
  ItemRepository itemRepository;

  private Item item1, item2;

  @BeforeEach
  void setUp() {
    cartItemRepository.deleteAll()
        .then(itemRepository.deleteAll())
        .then(itemRepository.save(new Item("Товар 1", "Описание 1", "/img/1.jpg", 1000))
            .doOnNext(saved -> item1 = saved))
        .then(itemRepository.save(new Item("Товар 2", "Описание 2", "/img/2.jpg", 2000))
            .doOnNext(saved -> item2 = saved))
        .block();
  }

  @Test
  void findBySessionId_returnsOnlyThisSession() {
    cartItemRepository.saveAll(java.util.List.of(
        new CartItem("session-1", item1.getId(), 2),
        new CartItem("session-1", item2.getId(), 1),
        new CartItem("session-2", item1.getId(), 3)
    )).then().block();

    StepVerifier.create(cartItemRepository.findBySessionId("session-1").collectList())
        .assertNext(items -> {
          assertThat(items).hasSize(2);
          assertThat(items).allMatch(ci -> ci.getSessionId().equals("session-1"));
        })
        .verifyComplete();
  }

  @Test
  void findBySessionId_otherSession_returnsEmpty() {
    cartItemRepository.save(new CartItem("session-1", item1.getId(), 1)).block();

    StepVerifier.create(cartItemRepository.findBySessionId("session-X").collectList())
        .assertNext(items -> assertThat(items).isEmpty())
        .verifyComplete();
  }

  @Test
  void findBySessionIdAndItemId_found() {
    cartItemRepository.save(new CartItem("session-1", item1.getId(), 5)).block();

    StepVerifier.create(
        cartItemRepository.findBySessionIdAndItemId("session-1", item1.getId()))
        .assertNext(ci -> assertThat(ci.getCount()).isEqualTo(5))
        .verifyComplete();
  }

  @Test
  void findBySessionIdAndItemId_differentSession_returnsEmpty() {
    cartItemRepository.save(new CartItem("session-1", item1.getId(), 5)).block();

    StepVerifier.create(
        cartItemRepository.findBySessionIdAndItemId("session-2", item1.getId()))
        .verifyComplete(); // Mono.empty() — no items
  }

  @Test
  void deleteBySessionId_removesOnlyTargetSession() {
    cartItemRepository.saveAll(java.util.List.of(
        new CartItem("session-1", item1.getId(), 1),
        new CartItem("session-1", item2.getId(), 1),
        new CartItem("session-2", item1.getId(), 1)
    )).then().block();

    cartItemRepository.deleteBySessionId("session-1").block();

    StepVerifier.create(cartItemRepository.findBySessionId("session-1").collectList())
        .assertNext(items -> assertThat(items).isEmpty())
        .verifyComplete();

    StepVerifier.create(cartItemRepository.findBySessionId("session-2").collectList())
        .assertNext(items -> assertThat(items).hasSize(1))
        .verifyComplete();
  }
}
