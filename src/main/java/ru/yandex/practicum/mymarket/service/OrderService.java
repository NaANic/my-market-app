package ru.yandex.practicum.mymarket.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.CustomerOrder;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.entity.OrderItem;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrderService {

  private final OrderRepository orderRepository;
  private final CartService cartService;

  public OrderService(OrderRepository orderRepository,
      CartService cartService) {
    this.orderRepository = orderRepository;
    this.cartService = cartService;
  }

  @Transactional
  public Long createOrder(String sessionId) {
    List<CartItem> cartItems = cartService.getCartItems(sessionId);
    if (cartItems.isEmpty()) {
      throw new RuntimeException("Корзина пуста");
    }

    long totalSum = cartItems.stream()
        .mapToLong(ci -> ci.getItem().getPrice() * ci.getCount())
        .sum();

    CustomerOrder order = new CustomerOrder(sessionId, totalSum);

    for (CartItem ci : cartItems) {
      Item item = ci.getItem();
      OrderItem oi = new OrderItem(
          item.getId(),
          item.getTitle(),
          item.getPrice(),
          ci.getCount()
      );
      order.addItem(oi);
    }

    CustomerOrder saved = orderRepository.save(order);
    cartService.clearCart(sessionId);

    return saved.getId();
  }

  public List<CustomerOrder> getOrders(String sessionId) {
    return orderRepository.findBySessionIdOrderByCreatedAtDesc(sessionId);
  }

  public CustomerOrder getOrder(long id) {
    return orderRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Заказ не найден: id=" + id));
  }
}
