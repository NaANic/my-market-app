package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ru.yandex.practicum.mymarket.dto.CartAction;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.ItemService;
import ru.yandex.practicum.mymarket.service.OrderService;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
class CartControllerTest {

  @Autowired
  MockMvc mockMvc;

  @MockitoBean
  ItemService itemService;

  @MockitoBean
  CartService cartService;

  @MockitoBean
  OrderService orderService;

  @Test
  void getCart_returnsCartTemplate() throws Exception {
    Item item = createItem(1L, "Мяч", 2500);
    when(cartService.getCartItems(any())).thenReturn(
        List.of(new CartItem("s", item, 2)));
    when(cartService.getCartTotal(any())).thenReturn(5000L);

    mockMvc.perform(get("/cart/items"))
        .andExpect(status().isOk())
        .andExpect(view().name("cart"))
        .andExpect(model().attributeExists("items"))
        .andExpect(model().attribute("total", 5000L));
  }

  @Test
  void getCart_emptyCart_totalZero() throws Exception {
    when(cartService.getCartItems(any())).thenReturn(List.of());
    when(cartService.getCartTotal(any())).thenReturn(0L);

    mockMvc.perform(get("/cart/items"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("total", 0L));
  }

  @Test
  void postCart_delete_callsHandleAction() throws Exception {
    when(cartService.getCartItems(any())).thenReturn(List.of());
    when(cartService.getCartTotal(any())).thenReturn(0L);

    mockMvc.perform(post("/cart/items")
            .param("id", "1")
            .param("action", "DELETE"))
        .andExpect(status().isOk())
        .andExpect(view().name("cart"));

    verify(cartService).handleAction(any(), eq(1L), eq(CartAction.DELETE));
  }

  @Test
  void postCart_plus_callsHandleAction() throws Exception {
    when(cartService.getCartItems(any())).thenReturn(List.of());
    when(cartService.getCartTotal(any())).thenReturn(0L);

    mockMvc.perform(post("/cart/items")
            .param("id", "3")
            .param("action", "PLUS"))
        .andExpect(status().isOk());

    verify(cartService).handleAction(any(), eq(3L), eq(CartAction.PLUS));
  }

  private Item createItem(Long id, String title, long price) {
    Item item = new Item(title, "Desc", "/img/t.jpg", price);
    item.setId(id);
    return item;
  }
}
