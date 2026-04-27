package ru.yandex.practicum.mymarket.service;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartAction;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.exception.EntityNotFoundException;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

import java.util.List;
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

  public Mono<Void> handleAction(Long userId, long itemId, CartAction action) {
    return switch (action) {
      case PLUS   -> addItem(userId, itemId);
      case MINUS  -> decreaseItem(userId, itemId);
      case DELETE -> removeItem(userId, itemId);
    };
  }

  // Used by OrderService to iterate cart items before clearing them
  public Flux<CartItem> getCartItems(Long userId) {
    return cartItemRepository.findByUserId(userId);
  }

  // Enriches each CartItem with its full Item data; used by controllers and OrderService.
  // Two-query approach: 1 query for cart rows + 1 IN query for all items — no N+1.
  public Flux<ItemDto> getCartItemDtos(Long userId) {
    return cartItemRepository.findByUserId(userId)
        .collectList()
        .flatMapMany(cartItems -> {
          if (cartItems.isEmpty()) return Flux.empty();
          List<Long> ids = cartItems.stream().map(CartItem::getItemId).toList();
          return itemRepository.findAllByIdIn(ids)
              .collectMap(Item::getId)
              .flatMapMany(itemMap -> Flux.fromIterable(cartItems)
                  .flatMap(ci -> {
                    Item item = itemMap.get(ci.getItemId());
                    if (item == null) {
                      return Mono.error(new EntityNotFoundException("Товар", ci.getItemId()));
                    }
                    return Mono.just(ItemDto.from(item, ci.getCount()));
                  }));
        });
  }

  public Mono<Integer> getItemCount(Long userId, long itemId) {
    return cartItemRepository.findByUserIdAndItemId(userId, itemId)
        .map(CartItem::getCount)
        .defaultIfEmpty(0);
  }

  public Mono<Map<Long, Integer>> getCartItemCounts(Long userId) {
    return cartItemRepository.findByUserId(userId)
        .collectMap(CartItem::getItemId, CartItem::getCount);
  }

  public Mono<Long> getCartTotal(Long userId) {
    return cartItemRepository.findByUserId(userId)
        .collectList()
        .flatMap(cartItems -> {
          if (cartItems.isEmpty()) return Mono.just(0L);
          List<Long> ids = cartItems.stream().map(CartItem::getItemId).toList();
          return itemRepository.findAllByIdIn(ids)
              .collectMap(Item::getId)
              .map(itemMap -> cartItems.stream()
                  .mapToLong(ci -> {
                    Item item = itemMap.get(ci.getItemId());
                    return item != null ? item.getPrice() * (long) ci.getCount() : 0L;
                  })
                  .sum());
        });
  }

  public Mono<Void> addItem(Long userId, long itemId) {
    return cartItemRepository.findByUserIdAndItemId(userId, itemId)
        .flatMap(ci -> {
          ci.setCount(ci.getCount() + 1);
          return cartItemRepository.save(ci);
        })
        .switchIfEmpty(
            itemRepository.findById(itemId)
                .switchIfEmpty(Mono.error(new EntityNotFoundException("Товар", itemId)))
                .flatMap(item -> cartItemRepository.save(new CartItem(userId, item.getId(), 1)))
        )
        .then();
  }

  public Mono<Void> decreaseItem(Long userId, long itemId) {
    return cartItemRepository.findByUserIdAndItemId(userId, itemId)
        .flatMap(ci -> {
          if (ci.getCount() <= 1) {
            return cartItemRepository.delete(ci);
          }
          ci.setCount(ci.getCount() - 1);
          return cartItemRepository.save(ci).then();
        });
  }

  public Mono<Void> removeItem(Long userId, long itemId) {
    return cartItemRepository.findByUserIdAndItemId(userId, itemId)
        .flatMap(cartItemRepository::delete);
  }

  public Mono<Void> clearCart(Long userId) {
    return cartItemRepository.deleteByUserId(userId);
  }
}
