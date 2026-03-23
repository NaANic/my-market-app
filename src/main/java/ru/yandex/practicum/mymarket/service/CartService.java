package ru.yandex.practicum.mymarket.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.mymarket.dto.CartAction;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CartService {

  private final CartItemRepository cartItemRepository;
  private final ItemRepository itemRepository;

  public CartService(CartItemRepository cartItemRepository,
      ItemRepository itemRepository) {
    this.cartItemRepository = cartItemRepository;
    this.itemRepository = itemRepository;
  }

  @Transactional
  public void handleAction(String sessionId, long itemId, CartAction action) {
    switch (action) {
      case PLUS   -> addItem(sessionId, itemId);
      case MINUS  -> decreaseItem(sessionId, itemId);
      case DELETE -> removeItem(sessionId, itemId);
    }
  }

  public List<CartItem> getCartItems(String sessionId) {
    return cartItemRepository.findBySessionId(sessionId);
  }

  public int getItemCount(String sessionId, long itemId) {
    return cartItemRepository.findBySessionIdAndItemId(sessionId, itemId)
        .map(CartItem::getCount)
        .orElse(0);
  }

  public Map<Long, Integer> getCartItemCounts(String sessionId) {
    return cartItemRepository.findBySessionId(sessionId).stream()
        .collect(Collectors.toMap(
            ci -> ci.getItem().getId(),
            CartItem::getCount
        ));
  }

  public long getCartTotal(String sessionId) {
    return cartItemRepository.findBySessionId(sessionId).stream()
        .mapToLong(ci -> ci.getItem().getPrice() * ci.getCount())
        .sum();
  }

  @Transactional
  public void addItem(String sessionId, long itemId) {
    var existing = cartItemRepository.findBySessionIdAndItemId(sessionId, itemId);

    if (existing.isPresent()) {
      CartItem ci = existing.get();
      ci.setCount(ci.getCount() + 1);
      cartItemRepository.save(ci);
    } else {
      Item item = itemRepository.findById(itemId)
          .orElseThrow(() -> new RuntimeException("Товар не найден: " + itemId));
      cartItemRepository.save(new CartItem(sessionId, item, 1));
    }
  }

  @Transactional
  public void decreaseItem(String sessionId, long itemId) {
    cartItemRepository.findBySessionIdAndItemId(sessionId, itemId)
        .ifPresent(ci -> {
          if (ci.getCount() <= 1) {
            cartItemRepository.delete(ci);
          } else {
            ci.setCount(ci.getCount() - 1);
            cartItemRepository.save(ci);
          }
        });
  }

  @Transactional
  public void removeItem(String sessionId, long itemId) {
    cartItemRepository.findBySessionIdAndItemId(sessionId, itemId)
        .ifPresent(cartItemRepository::delete);
  }

  @Transactional
  public void clearCart(String sessionId) {
    cartItemRepository.deleteBySessionId(sessionId);
  }
}
