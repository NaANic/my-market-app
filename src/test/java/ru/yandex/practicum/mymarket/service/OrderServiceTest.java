package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.CustomerOrder;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock
  OrderRepository orderRepository;

  @Mock
  CartService cartService;

  @InjectMocks
  OrderService orderService;

  @Test
  void createOrder_success_createsOrderWithSnapshots() {
    Item item = createItem(1L, "Товар", 1500);
    CartItem ci = new CartItem("s1", item, 3);
    when(cartService.getCartItems("s1")).thenReturn(List.of(ci));
    when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> {
      CustomerOrder order = inv.getArgument(0);
      ReflectionTestUtils.setField(order, "id", 42L);
      return order;
    });

    Long orderId = orderService.createOrder("s1");

    assertThat(orderId).isEqualTo(42L);
    verify(orderRepository).save(argThat(order ->
        order.getTotalSum() == 4500 &&
            order.getItems().size() == 1 &&
            order.getItems().get(0).getTitle().equals("Товар") &&
            order.getItems().get(0).getPrice() == 1500 &&
            order.getItems().get(0).getCount() == 3
    ));
    verify(cartService).clearCart("s1");
  }

  @Test
  void createOrder_emptyCart_throwsException() {
    when(cartService.getCartItems("s1")).thenReturn(List.of());

    assertThatThrownBy(() -> orderService.createOrder("s1"))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("пуста");
  }

  @Test
  void getOrder_existing_returnsOrder() {
    CustomerOrder order = new CustomerOrder("s1", 3000);
    ReflectionTestUtils.setField(order, "id", 1L);
    when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

    CustomerOrder result = orderService.getOrder(1L);

    assertThat(result.getTotalSum()).isEqualTo(3000);
  }

  @Test
  void getOrder_nonExisting_throwsException() {
    when(orderRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.getOrder(999L))
        .isInstanceOf(RuntimeException.class);
  }

  private Item createItem(Long id, String title, long price) {
    Item item = new Item(title, "Desc", "/img/t.jpg", price);
    item.setId(id);
    return item;
  }
}
