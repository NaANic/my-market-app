package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.dto.CartAction;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    when(cartItemRepository.findBySessionIdAndItemId("s1", 1L))
        .thenReturn(Mono.empty());
    when(itemRepository.findById(1L))
        .thenReturn(Mono.just(createItem(1L, "Test", 100)));
    when(cartItemRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    StepVerifier.create(cartService.addItem("s1", 1L))
        .verifyComplete();

    verify(cartItemRepository).save(argThat(ci ->
        ci.getSessionId().equals("s1") &&
            ci.getItemId().equals(1L) &&
            ci.getCount() == 1
    ));
  }

  @Test
  void addItem_existingItem_incrementsCount() {
    CartItem existing = new CartItem("s1", 1L, 3);
    when(cartItemRepository.findBySessionIdAndItemId("s1", 1L))
        .thenReturn(Mono.just(existing));
    when(cartItemRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    // itemRepository.findById is referenced during chain construction (as switchIfEmpty arg),
    // so it must be stubbed even though the switchIfEmpty branch won't be reached.
    when(itemRepository.findById(1L)).thenReturn(Mono.empty());

    StepVerifier.create(cartService.addItem("s1", 1L))
        .verifyComplete();

    assertThat(existing.getCount()).isEqualTo(4);
    verify(cartItemRepository).save(existing);
  }

  @Test
  void decreaseItem_countGreaterThanOne_decrementsCount() {
    CartItem ci = new CartItem("s1", 1L, 5);
    when(cartItemRepository.findBySessionIdAndItemId("s1", 1L))
        .thenReturn(Mono.just(ci));
    when(cartItemRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    StepVerifier.create(cartService.decreaseItem("s1", 1L))
        .verifyComplete();

    assertThat(ci.getCount()).isEqualTo(4);
    verify(cartItemRepository).save(ci);
    verify(cartItemRepository, never()).delete(any());
  }

  @Test
  void decreaseItem_countOne_removesItem() {
    CartItem ci = new CartItem("s1", 1L, 1);
    when(cartItemRepository.findBySessionIdAndItemId("s1", 1L))
        .thenReturn(Mono.just(ci));
    when(cartItemRepository.delete(any())).thenReturn(Mono.empty());

    StepVerifier.create(cartService.decreaseItem("s1", 1L))
        .verifyComplete();

    verify(cartItemRepository).delete(ci);
    verify(cartItemRepository, never()).save(any());
  }

  @Test
  void removeItem_existing_deletesCartItem() {
    CartItem ci = new CartItem("s1", 1L, 5);
    when(cartItemRepository.findBySessionIdAndItemId("s1", 1L))
        .thenReturn(Mono.just(ci));
    when(cartItemRepository.delete(any())).thenReturn(Mono.empty());

    StepVerifier.create(cartService.removeItem("s1", 1L))
        .verifyComplete();

    verify(cartItemRepository).delete(ci);
  }

  @Test
  void handleAction_delegatesToAddItem() {
    when(cartItemRepository.findBySessionIdAndItemId("s1", 1L))
        .thenReturn(Mono.empty());
    when(itemRepository.findById(1L))
        .thenReturn(Mono.just(createItem(1L, "T", 100)));
    when(cartItemRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    StepVerifier.create(cartService.handleAction("s1", 1L, CartAction.PLUS))
        .verifyComplete();
  }

  @Test
  void getCartTotal_sumsCorrectly() {
    when(cartItemRepository.findBySessionId("s1")).thenReturn(Flux.just(
        new CartItem("s1", 1L, 2),
        new CartItem("s1", 2L, 3)
    ));
    when(itemRepository.findAllByIdIn(any())).thenReturn(Flux.just(
        createItem(1L, "A", 1000),
        createItem(2L, "B", 2500)
    ));

    StepVerifier.create(cartService.getCartTotal("s1"))
        .assertNext(total -> assertThat(total).isEqualTo(9500L))
        .verifyComplete();
  }

  @Test
  void getCartTotal_emptyCart_returnsZero() {
    when(cartItemRepository.findBySessionId("s1")).thenReturn(Flux.empty());

    StepVerifier.create(cartService.getCartTotal("s1"))
        .assertNext(total -> assertThat(total).isEqualTo(0L))
        .verifyComplete();
  }

  @Test
  void getCartItemCounts_returnsMap() {
    when(cartItemRepository.findBySessionId("s1")).thenReturn(Flux.just(
        new CartItem("s1", 1L, 2),
        new CartItem("s1", 2L, 5)
    ));

    StepVerifier.create(cartService.getCartItemCounts("s1"))
        .assertNext(counts -> assertThat(counts)
            .containsEntry(1L, 2)
            .containsEntry(2L, 5))
        .verifyComplete();
  }

  @Test
  void getItemCount_itemNotInCart_returnsZero() {
    when(cartItemRepository.findBySessionIdAndItemId("s1", 1L))
        .thenReturn(Mono.empty());

    StepVerifier.create(cartService.getItemCount("s1", 1L))
        .assertNext(count -> assertThat(count).isEqualTo(0))
        .verifyComplete();
  }

  @Test
  void getItemCount_itemInCart_returnsCount() {
    when(cartItemRepository.findBySessionIdAndItemId("s1", 1L))
        .thenReturn(Mono.just(new CartItem("s1", 1L, 3)));

    StepVerifier.create(cartService.getItemCount("s1", 1L))
        .assertNext(count -> assertThat(count).isEqualTo(3))
        .verifyComplete();
  }

  @Test
  void clearCart_delegatesToRepository() {
    when(cartItemRepository.deleteBySessionId("s1")).thenReturn(Mono.empty());

    StepVerifier.create(cartService.clearCart("s1"))
        .verifyComplete();

    verify(cartItemRepository).deleteBySessionId(eq("s1"));
  }

  private Item createItem(Long id, String title, long price) {
    Item item = new Item(title, "Desc", "/img/t.jpg", price);
    item.setId(id);
    return item;
  }
}
