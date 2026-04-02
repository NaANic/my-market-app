package ru.yandex.practicum.mymarket.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartAction;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

import java.util.Map;

@Service
public class CartService {

  private final CartItemRepository cartItemRepository;
  private final ItemRepository itemRepository;

  public CartService(CartItemRepository cartItemRepository,
      ItemRepository itemRepository) {
    this.cartItemRepository = cartItemRepository;
    this.itemRepository = itemRepository;
  }

  public Mono<Void> handleAction(String sessionId, long itemId, CartAction action) {
    return switch (action) {
      case PLUS   -> addItem(sessionId, itemId);
      case MINUS  -> decreaseItem(sessionId, itemId);
      case DELETE -> removeItem(sessionId, itemId);
    };
  }

  // Used by OrderService to iterate cart items before clearing them
  public Flux<CartItem> getCartItems(String sessionId) {
    return cartItemRepository.findBySessionId(sessionId);
  }

  // Enriches each CartItem with its full Item data; used by controllers and OrderService
  public Flux<ItemDto> getCartItemDtos(String sessionId) {
    return cartItemRepository.findBySessionId(sessionId)
        .flatMap(ci -> itemRepository.findById(ci.getItemId())
            .map(item -> ItemDto.from(item, ci.getCount())));
  }

  public Mono<Integer> getItemCount(String sessionId, long itemId) {
    return cartItemRepository.findBySessionIdAndItemId(sessionId, itemId)
        .map(CartItem::getCount)
        .defaultIfEmpty(0);
  }

  public Mono<Map<Long, Integer>> getCartItemCounts(String sessionId) {
    return cartItemRepository.findBySessionId(sessionId)
        .collectMap(CartItem::getItemId, CartItem::getCount);
  }

  public Mono<Long> getCartTotal(String sessionId) {
    return cartItemRepository.findBySessionId(sessionId)
        .flatMap(ci -> itemRepository.findById(ci.getItemId())
            .map(item -> item.getPrice() * (long) ci.getCount()))
        .reduce(0L, Long::sum);
  }

  public Mono<Void> addItem(String sessionId, long itemId) {
    // flatMap must return Mono<CartItem> (non-empty) so that switchIfEmpty is not triggered
    // after updating an existing item. The .then() is applied at the end of the whole chain.
    return cartItemRepository.findBySessionIdAndItemId(sessionId, itemId)
        .flatMap(ci -> {
          ci.setCount(ci.getCount() + 1);
          return cartItemRepository.save(ci);
        })
        .switchIfEmpty(
            itemRepository.findById(itemId)
                .switchIfEmpty(Mono.error(new RuntimeException("Товар не найден: " + itemId)))
                .flatMap(item -> cartItemRepository.save(new CartItem(sessionId, item.getId(), 1)))
        )
        .then();
  }

  public Mono<Void> decreaseItem(String sessionId, long itemId) {
    return cartItemRepository.findBySessionIdAndItemId(sessionId, itemId)
        .flatMap(ci -> {
          if (ci.getCount() <= 1) {
            return cartItemRepository.delete(ci);
          }
          ci.setCount(ci.getCount() - 1);
          return cartItemRepository.save(ci).then();
        });
  }

  public Mono<Void> removeItem(String sessionId, long itemId) {
    return cartItemRepository.findBySessionIdAndItemId(sessionId, itemId)
        .flatMap(cartItemRepository::delete);
  }

  public Mono<Void> clearCart(String sessionId) {
    return cartItemRepository.deleteBySessionId(sessionId);
  }
}
