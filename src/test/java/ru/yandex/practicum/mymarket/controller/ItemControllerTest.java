package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.yandex.practicum.mymarket.dto.CartAction;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.ItemService;
import ru.yandex.practicum.mymarket.service.OrderService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest
class ItemControllerTest {

  @Autowired
  MockMvc mockMvc;

  @MockitoBean
  ItemService itemService;

  @MockitoBean
  CartService cartService;

  @MockitoBean
  OrderService orderService;

  @Test
  void getItems_returnsItemsTemplate() throws Exception {
    when(itemService.findItems(any(), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(createItem(1L, "Test", 500))));
    when(cartService.getCartItemCounts(any())).thenReturn(Map.of());

    mockMvc.perform(get("/items"))
        .andExpect(status().isOk())
        .andExpect(view().name("items"))
        .andExpect(model().attributeExists("items", "sort", "paging"));
  }

  @Test
  void getItems_defaultParams() throws Exception {
    when(itemService.findItems(any(), any())).thenReturn(new PageImpl<>(List.of()));
    when(cartService.getCartItemCounts(any())).thenReturn(Map.of());

    mockMvc.perform(get("/items"))
        .andExpect(model().attribute("sort", "NO"));

    verify(itemService).findItems(isNull(), argThat(pageable ->
        pageable.getPageNumber() == 0 && pageable.getPageSize() == 5
    ));
  }

  @Test
  void getItems_withSearch() throws Exception {
    when(itemService.findItems(any(), any())).thenReturn(new PageImpl<>(List.of()));
    when(cartService.getCartItemCounts(any())).thenReturn(Map.of());

    mockMvc.perform(get("/items").param("search", "мяч"))
        .andExpect(model().attribute("search", "мяч"));

    verify(itemService).findItems(eq("мяч"), any());
  }

  @Test
  void getItems_gridPadsDummyItems() throws Exception {
    when(itemService.findItems(any(), any())).thenReturn(new PageImpl<>(List.of(
        createItem(1L, "A", 100),
        createItem(2L, "B", 200)
    )));
    when(cartService.getCartItemCounts(any())).thenReturn(Map.of());

    MvcResult result = mockMvc.perform(get("/items"))
        .andExpect(status().isOk())
        .andReturn();

    @SuppressWarnings("unchecked")
    List<List<ItemDto>> grid = (List<List<ItemDto>>)
        result.getModelAndView().getModel().get("items");

    assertThat(grid).hasSize(1);
    assertThat(grid.get(0)).hasSize(3);
    assertThat(grid.get(0).get(2).id()).isEqualTo(-1);
  }

  @Test
  void getRootPath_sameAsGetItems() throws Exception {
    when(itemService.findItems(any(), any())).thenReturn(new PageImpl<>(List.of()));
    when(cartService.getCartItemCounts(any())).thenReturn(Map.of());

    mockMvc.perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(view().name("items"));
  }

  @Test
  void postItems_redirectsWithParams() throws Exception {
    mockMvc.perform(post("/items")
            .param("id", "1")
            .param("action", "PLUS")
            .param("sort", "ALPHA")
            .param("pageNumber", "2")
            .param("pageSize", "10"))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location",
            org.hamcrest.Matchers.allOf(
                org.hamcrest.Matchers.containsString("/items?"),
                org.hamcrest.Matchers.containsString("sort=ALPHA"),
                org.hamcrest.Matchers.containsString("pageNumber=2"),
                org.hamcrest.Matchers.containsString("pageSize=10"))));

    verify(cartService).handleAction(any(), eq(1L), eq(CartAction.PLUS));
  }

  @Test
  void getItem_returnsItemTemplate() throws Exception {
    when(itemService.findById(1L)).thenReturn(createItem(1L, "Мяч", 2500));
    when(cartService.getItemCount(any(), eq(1L))).thenReturn(2);

    mockMvc.perform(get("/items/1"))
        .andExpect(status().isOk())
        .andExpect(view().name("item"))
        .andExpect(model().attributeExists("item"));
  }

  @Test
  void postItem_modifiesCartAndReturnsTemplate() throws Exception {
    when(itemService.findById(1L)).thenReturn(createItem(1L, "Мяч", 2500));
    when(cartService.getItemCount(any(), eq(1L))).thenReturn(1);

    mockMvc.perform(post("/items/1").param("action", "PLUS"))
        .andExpect(status().isOk())
        .andExpect(view().name("item"));

    verify(cartService).handleAction(any(), eq(1L), eq(CartAction.PLUS));
  }

  private Item createItem(Long id, String title, long price) {
    Item item = new Item(title, "Description", "/img/test.jpg", price);
    item.setId(id);
    return item;
  }
}
