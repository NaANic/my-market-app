package ru.yandex.practicum.mymarket.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.config.R2dbcConfig;
import ru.yandex.practicum.mymarket.config.TestDataR2dbcConfig;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.entity.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataR2dbcTest
@Import({R2dbcConfig.class, TestDataR2dbcConfig.class})
@ActiveProfiles("test")
class CartItemRepositoryTest {

  @Autowired CartItemRepository cartItemRepository;
  @Autowired ItemRepository itemRepository;
  @Autowired UserRepository userRepository;

  private Item item1, item2;

  // Use distinct user IDs (no users table seeded here, but FK is informational)
  private Long USER_1;
  private Long USER_2;

  @BeforeEach
  void setUp() {
    cartItemRepository.deleteAll()
        .then(itemRepository.deleteAll())
        .then(userRepository.deleteAll())
        .then(userRepository.save(new User("alice", "x"))
            .doOnNext(u -> { /* stays USER_1 ref */ }))
        .then(userRepository.save(new User("bob", "x")))
        .then(itemRepository.save(new Item("Товар 1", "Описание 1", "/img/1.jpg", 1000))
            .doOnNext(saved -> item1 = saved))
        .then(itemRepository.save(new Item("Товар 2", "Описание 2", "/img/2.jpg", 2000))
            .doOnNext(saved -> item2 = saved))
        .block();

    // Capture actual user IDs since H2 IDENTITY may not give us 1, 2
    var userIds = userRepository.findAll().collectList().block();
    USER_1 = userIds.get(0).getId();
    USER_2 = userIds.get(1).getId();
  }

  @Test
  void findByUserId_returnsOnlyThisUser() {
    cartItemRepository.saveAll(List.of(
        new CartItem(USER_1, item1.getId(), 2),
        new CartItem(USER_1, item2.getId(), 1),
        new CartItem(USER_2, item1.getId(), 3)
    )).then().block();

    StepVerifier.create(cartItemRepository.findByUserId(USER_1).collectList())
        .assertNext(items -> {
          assertThat(items).hasSize(2);
          assertThat(items).allMatch(ci -> ci.getUserId().equals(USER_1));
        })
        .verifyComplete();
  }

  @Test
  void findByUserId_otherUser_returnsEmpty() {
    cartItemRepository.save(new CartItem(USER_1, item1.getId(), 1)).block();

    StepVerifier.create(cartItemRepository.findByUserId(999L).collectList())
        .assertNext(items -> assertThat(items).isEmpty())
        .verifyComplete();
  }

  @Test
  void findByUserIdAndItemId_found() {
    cartItemRepository.save(new CartItem(USER_1, item1.getId(), 5)).block();

    StepVerifier.create(
            cartItemRepository.findByUserIdAndItemId(USER_1, item1.getId()))
        .assertNext(ci -> assertThat(ci.getCount()).isEqualTo(5))
        .verifyComplete();
  }

  @Test
  void findByUserIdAndItemId_differentUser_returnsEmpty() {
    cartItemRepository.save(new CartItem(USER_1, item1.getId(), 5)).block();

    StepVerifier.create(
            cartItemRepository.findByUserIdAndItemId(USER_2, item1.getId()))
        .verifyComplete();
  }

  @Test
  void deleteByUserId_removesOnlyTargetUser() {
    cartItemRepository.saveAll(List.of(
        new CartItem(USER_1, item1.getId(), 1),
        new CartItem(USER_1, item2.getId(), 1),
        new CartItem(USER_2, item1.getId(), 1)
    )).then().block();

    cartItemRepository.deleteByUserId(USER_1).block();

    StepVerifier.create(cartItemRepository.findByUserId(USER_1).collectList())
        .assertNext(items -> assertThat(items).isEmpty())
        .verifyComplete();

    StepVerifier.create(cartItemRepository.findByUserId(USER_2).collectList())
        .assertNext(items -> assertThat(items).hasSize(1))
        .verifyComplete();
  }
}
