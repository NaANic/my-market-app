package ru.yandex.practicum.mymarket.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Item;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
class CartItemRepositoryTest {

  @Autowired
  CartItemRepository cartItemRepository;

  @Autowired
  ItemRepository itemRepository;

  @Autowired
  TestEntityManager em;

  private Item item1, item2;

  @BeforeEach
  void setUp() {
    cartItemRepository.deleteAll();
    itemRepository.deleteAll();
    item1 = itemRepository.save(new Item("Товар 1", "Описание 1", "/img/1.jpg", 1000));
    item2 = itemRepository.save(new Item("Товар 2", "Описание 2", "/img/2.jpg", 2000));
  }

  @Test
  void findBySessionId_returnsOnlyThisSession() {
    cartItemRepository.save(new CartItem("session-1", item1, 2));
    cartItemRepository.save(new CartItem("session-1", item2, 1));
    cartItemRepository.save(new CartItem("session-2", item1, 3));

    List<CartItem> result = cartItemRepository.findBySessionId("session-1");

    assertThat(result).hasSize(2);
    assertThat(result).allMatch(ci -> ci.getSessionId().equals("session-1"));
  }

  @Test
  void findBySessionId_loadsItemEagerly() {
    cartItemRepository.save(new CartItem("session-1", item1, 1));

    List<CartItem> result = cartItemRepository.findBySessionId("session-1");

    assertThat(result.get(0).getItem().getTitle()).isEqualTo("Товар 1");
    assertThat(result.get(0).getItem().getPrice()).isEqualTo(1000);
  }

  @Test
  void findBySessionIdAndItemId_found() {
    cartItemRepository.save(new CartItem("session-1", item1, 5));

    Optional<CartItem> result = cartItemRepository
        .findBySessionIdAndItemId("session-1", item1.getId());

    assertThat(result).isPresent();
    assertThat(result.get().getCount()).isEqualTo(5);
  }

  @Test
  void findBySessionIdAndItemId_notFound_differentSession() {
    cartItemRepository.save(new CartItem("session-1", item1, 5));

    Optional<CartItem> result = cartItemRepository
        .findBySessionIdAndItemId("session-2", item1.getId());

    assertThat(result).isEmpty();
  }

  @Test
  void deleteBySessionId_removesOnlyTargetSession() {
    cartItemRepository.save(new CartItem("session-1", item1, 1));
    cartItemRepository.save(new CartItem("session-1", item2, 1));
    cartItemRepository.save(new CartItem("session-2", item1, 1));
    em.flush();

    cartItemRepository.deleteBySessionId("session-1");
    em.flush();
    em.clear();

    assertThat(cartItemRepository.findBySessionId("session-1")).isEmpty();
    assertThat(cartItemRepository.findBySessionId("session-2")).hasSize(1);
  }
}
