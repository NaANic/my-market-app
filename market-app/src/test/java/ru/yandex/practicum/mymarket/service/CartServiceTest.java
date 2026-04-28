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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CartServiceTest {

  @Mock
  CartItemRepository cartItemRepository;

  @Mock
  ItemRepository itemRepository;

  @InjectMocks
  CartService cartService;

  private static final Long USER_ID = 1L;

  @Test
  void addItem_newItem_createsCartItemWithCountOne() {
    when(cartItemRepository.findByUserIdAndItemId(USER_ID, 1L))
        .thenReturn(Mono.empty());
    when(itemRepository.findById(1L))
        .thenReturn(Mono.just(createItem(1L, "Test", 100)));
    when(cartItemRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    StepVerifier.create(cartService.addItem(USER_ID, 1L))
        .verifyComplete();

    verify(cartItemRepository).save(argThat(ci ->
        ci.getUserId().equals(USER_ID) &&
            ci.getItemId().equals(1L) &&
            ci.getCount() == 1
    ));
  }

  @Test
  void addItem_existingItem_incrementsCount() {
    CartItem existing = new CartItem(USER_ID, 1L, 3);
    when(cartItemRepository.findByUserIdAndItemId(USER_ID, 1L))
        .thenReturn(Mono.just(existing));
    when(cartItemRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    StepVerifier.create(cartService.addItem(USER_ID, 1L))
        .verifyComplete();

    assertThat(existing.getCount()).isEqualTo(4);
    verify(cartItemRepository).save(existing);
  }

  @Test
  void decreaseItem_countGreaterThanOne_decrementsCount() {
    CartItem ci = new CartItem(USER_ID, 1L, 5);
    when(cartItemRepository.findByUserIdAndItemId(USER_ID, 1L))
        .thenReturn(Mono.just(ci));
    when(cartItemRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    StepVerifier.create(cartService.decreaseItem(USER_ID, 1L))
        .verifyComplete();

    assertThat(ci.getCount()).isEqualTo(4);
    verify(cartItemRepository).save(ci);
    verify(cartItemRepository, never()).delete(any());
  }

  @Test
  void decreaseItem_countOne_removesItem() {
    CartItem ci = new CartItem(USER_ID, 1L, 1);
    when(cartItemRepository.findByUserIdAndItemId(USER_ID, 1L))
        .thenReturn(Mono.just(ci));
    when(cartItemRepository.delete(any())).thenReturn(Mono.empty());

    StepVerifier.create(cartService.decreaseItem(USER_ID, 1L))
        .verifyComplete();

    verify(cartItemRepository).delete(ci);
    verify(cartItemRepository, never()).save(any());
  }

  @Test
  void removeItem_existing_deletesCartItem() {
    CartItem ci = new CartItem(USER_ID, 1L, 5);
    when(cartItemRepository.findByUserIdAndItemId(USER_ID, 1L))
        .thenReturn(Mono.just(ci));
    when(cartItemRepository.delete(any())).thenReturn(Mono.empty());

    StepVerifier.create(cartService.removeItem(USER_ID, 1L))
        .verifyComplete();

    verify(cartItemRepository).delete(ci);
  }

  @Test
  void handleAction_delegatesToAddItem() {
    when(cartItemRepository.findByUserIdAndItemId(USER_ID, 1L))
        .thenReturn(Mono.empty());
    when(itemRepository.findById(1L))
        .thenReturn(Mono.just(createItem(1L, "T", 100)));
    when(cartItemRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    StepVerifier.create(cartService.handleAction(USER_ID, 1L, CartAction.PLUS))
        .verifyComplete();
  }

  @Test
  void getCartTotal_sumsCorrectly() {
    when(cartItemRepository.findByUserId(USER_ID)).thenReturn(Flux.just(
        new CartItem(USER_ID, 1L, 2),
        new CartItem(USER_ID, 2L, 3)
    ));
    when(itemRepository.findAllByIdIn(any())).thenReturn(Flux.just(
        createItem(1L, "A", 1000),
        createItem(2L, "B", 2500)
    ));

    StepVerifier.create(cartService.getCartTotal(USER_ID))
        .assertNext(total -> assertThat(total).isEqualTo(9500L))
        .verifyComplete();
  }

  @Test
  void getCartTotal_emptyCart_returnsZero() {
    when(cartItemRepository.findByUserId(USER_ID)).thenReturn(Flux.empty());

    StepVerifier.create(cartService.getCartTotal(USER_ID))
        .assertNext(total -> assertThat(total).isEqualTo(0L))
        .verifyComplete();
  }

  @Test
  void getCartItemCounts_returnsMap() {
    when(cartItemRepository.findByUserId(USER_ID)).thenReturn(Flux.just(
        new CartItem(USER_ID, 1L, 2),
        new CartItem(USER_ID, 2L, 5)
    ));

    StepVerifier.create(cartService.getCartItemCounts(USER_ID))
        .assertNext(counts -> assertThat(counts)
            .containsEntry(1L, 2)
            .containsEntry(2L, 5))
        .verifyComplete();
  }

  @Test
  void getItemCount_itemNotInCart_returnsZero() {
    when(cartItemRepository.findByUserIdAndItemId(USER_ID, 1L))
        .thenReturn(Mono.empty());

    StepVerifier.create(cartService.getItemCount(USER_ID, 1L))
        .assertNext(count -> assertThat(count).isEqualTo(0))
        .verifyComplete();
  }

  @Test
  void getItemCount_itemInCart_returnsCount() {
    when(cartItemRepository.findByUserIdAndItemId(USER_ID, 1L))
        .thenReturn(Mono.just(new CartItem(USER_ID, 1L, 3)));

    StepVerifier.create(cartService.getItemCount(USER_ID, 1L))
        .assertNext(count -> assertThat(count).isEqualTo(3))
        .verifyComplete();
  }

  @Test
  void clearCart_delegatesToRepository() {
    when(cartItemRepository.deleteByUserId(USER_ID)).thenReturn(Mono.empty());

    StepVerifier.create(cartService.clearCart(USER_ID))
        .verifyComplete();

    verify(cartItemRepository).deleteByUserId(eq(USER_ID));
  }

  private Item createItem(Long id, String title, long price) {
    Item item = new Item(title, "Desc", "/img/t.jpg", price);
    item.setId(id);
    return item;
  }
}
