package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

  @Mock
  CartItemRepository cartItemRepository;

  @Mock
  ItemRepository itemRepository;

  @InjectMocks
  CartService cartService;

  @Test
  void addItem_newItem_createsCartItemWithCountOne() {
    Item item = createItem(1L, "Test", 100);
    when(cartItemRepository.findBySessionIdAndItemId("s1", 1L))
        .thenReturn(Optional.empty());
    when(itemRepository.findById(1L))
        .thenReturn(Optional.of(item));

    cartService.addItem("s1", 1L);

    verify(cartItemRepository).save(argThat(ci ->
        ci.getSessionId().equals("s1") &&
            ci.getItem().getId() == 1L &&
            ci.getCount() == 1
    ));
  }

  @Test
  void addItem_existingItem_incrementsCount() {
    Item item = createItem(1L, "Test", 100);
    CartItem existing = new CartItem("s1", item, 3);
    when(cartItemRepository.findBySessionIdAndItemId("s1", 1L))
        .thenReturn(Optional.of(existing));

    cartService.addItem("s1", 1L);

    assertThat(existing.getCount()).isEqualTo(4);
    verify(cartItemRepository).save(existing);
  }

  @Test
  void decreaseItem_countGreaterThanOne_decrementsCount() {
    CartItem ci = new CartItem("s1", createItem(1L, "T", 100), 5);
    when(cartItemRepository.findBySessionIdAndItemId("s1", 1L))
        .thenReturn(Optional.of(ci));

    cartService.decreaseItem("s1", 1L);

    assertThat(ci.getCount()).isEqualTo(4);
    verify(cartItemRepository).save(ci);
    verify(cartItemRepository, never()).delete(any());
  }

  @Test
  void decreaseItem_countOne_removesItem() {
    CartItem ci = new CartItem("s1", createItem(1L, "T", 100), 1);
    when(cartItemRepository.findBySessionIdAndItemId("s1", 1L))
        .thenReturn(Optional.of(ci));

    cartService.decreaseItem("s1", 1L);

    verify(cartItemRepository).delete(ci);
    verify(cartItemRepository, never()).save(any());
  }

  @Test
  void removeItem_existing_deletesCartItem() {
    CartItem ci = new CartItem("s1", createItem(1L, "T", 100), 5);
    when(cartItemRepository.findBySessionIdAndItemId("s1", 1L))
        .thenReturn(Optional.of(ci));

    cartService.removeItem("s1", 1L);

    verify(cartItemRepository).delete(ci);
  }

  @Test
  void getCartTotal_sumsCorrectly() {
    Item item1 = createItem(1L, "A", 1000);
    Item item2 = createItem(2L, "B", 2500);
    when(cartItemRepository.findBySessionId("s1"))
        .thenReturn(List.of(
            new CartItem("s1", item1, 2),
            new CartItem("s1", item2, 3)
        ));

    long total = cartService.getCartTotal("s1");

    assertThat(total).isEqualTo(9500);
  }

  @Test
  void getCartTotal_emptyCart_returnsZero() {
    when(cartItemRepository.findBySessionId("s1")).thenReturn(List.of());

    assertThat(cartService.getCartTotal("s1")).isEqualTo(0L);
  }

  @Test
  void getCartItemCounts_returnsMap() {
    when(cartItemRepository.findBySessionId("s1")).thenReturn(List.of(
        new CartItem("s1", createItem(1L, "A", 100), 2),
        new CartItem("s1", createItem(2L, "B", 200), 5)
    ));

    Map<Long, Integer> counts = cartService.getCartItemCounts("s1");

    assertThat(counts).containsEntry(1L, 2).containsEntry(2L, 5);
  }

  @Test
  void getItemCount_itemNotInCart_returnsZero() {
    when(cartItemRepository.findBySessionIdAndItemId("s1", 1L))
        .thenReturn(Optional.empty());

    assertThat(cartService.getItemCount("s1", 1L)).isEqualTo(0);
  }

  private Item createItem(Long id, String title, long price) {
    Item item = new Item(title, "Desc", "/img/t.jpg", price);
    item.setId(id);
    return item;
  }
}
