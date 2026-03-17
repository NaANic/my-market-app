package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.mymarket.entity.CustomerOrder;
import ru.yandex.practicum.mymarket.entity.OrderItem;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.ItemService;
import ru.yandex.practicum.mymarket.service.OrderService;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
class OrderControllerTest {

  @Autowired
  MockMvc mockMvc;

  @MockitoBean
  ItemService itemService;

  @MockitoBean
  CartService cartService;

  @MockitoBean
  OrderService orderService;

  @Test
  void getOrders_returnsOrdersTemplate() throws Exception {
    CustomerOrder order = createOrder(1L, "s1", 3000);
    when(orderService.getOrders(any())).thenReturn(List.of(order));

    mockMvc.perform(get("/orders"))
        .andExpect(status().isOk())
        .andExpect(view().name("orders"))
        .andExpect(model().attributeExists("orders"));
  }

  @Test
  void getOrder_returnsOrderTemplate() throws Exception {
    CustomerOrder order = createOrder(1L, "s1", 5000);
    when(orderService.getOrder(1L)).thenReturn(order);

    mockMvc.perform(get("/orders/1"))
        .andExpect(status().isOk())
        .andExpect(view().name("order"))
        .andExpect(model().attributeExists("order"))
        .andExpect(model().attribute("newOrder", false));
  }

  @Test
  void getOrder_withNewOrderFlag() throws Exception {
    CustomerOrder order = createOrder(1L, "s1", 5000);
    when(orderService.getOrder(1L)).thenReturn(order);

    mockMvc.perform(get("/orders/1").param("newOrder", "true"))
        .andExpect(model().attribute("newOrder", true));
  }

  @Test
  void buy_redirectsToNewOrder() throws Exception {
    when(orderService.createOrder(any())).thenReturn(42L);

    mockMvc.perform(post("/buy"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/orders/42?newOrder=true"));
  }

  private CustomerOrder createOrder(Long id, String sessionId, long totalSum) {
    CustomerOrder order = new CustomerOrder(sessionId, totalSum);
    ReflectionTestUtils.setField(order, "id", id);
    order.addItem(new OrderItem(1L, "Товар", 1500, 2));
    return order;
  }
}
