package ru.yandex.practicum.mymarket.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FullFlowIntegrationTest {

  @Autowired MockMvc mockMvc;
  @Autowired ItemRepository itemRepository;
  @Autowired CartItemRepository cartItemRepository;
  @Autowired OrderRepository orderRepository;

  private Item testItem;

  @BeforeEach
  void setUp() {
    cartItemRepository.deleteAll();
    orderRepository.deleteAll();
    itemRepository.deleteAll();
    testItem = itemRepository.save(
        new Item("Тестовый мяч", "Мяч для тестов", "/img/ball.jpg", 1500)
    );
  }

  @Test
  void fullPurchaseFlow() throws Exception {
    MockHttpSession session = new MockHttpSession();

    // 1. Каталог доступен
    mockMvc.perform(get("/items").session(session))
        .andExpect(status().isOk())
        .andExpect(view().name("items"));

    // 2. Добавить товар (2 штуки)
    mockMvc.perform(post("/items/" + testItem.getId())
            .param("action", "PLUS").session(session))
        .andExpect(status().isOk());
    mockMvc.perform(post("/items/" + testItem.getId())
            .param("action", "PLUS").session(session))
        .andExpect(status().isOk());

    // 3. Корзина: 2 × 1500 = 3000
    mockMvc.perform(get("/cart/items").session(session))
        .andExpect(status().isOk())
        .andExpect(model().attribute("total", 3000L));

    // 4. Минус 1: осталось 1 × 1500 = 1500
    mockMvc.perform(post("/cart/items")
            .param("id", String.valueOf(testItem.getId()))
            .param("action", "MINUS").session(session))
        .andExpect(model().attribute("total", 1500L));

    // 5. Купить
    MvcResult buyResult = mockMvc.perform(post("/buy").session(session))
        .andExpect(status().is3xxRedirection())
        .andReturn();
    String redirectUrl = buyResult.getResponse().getRedirectedUrl();
    assertThat(redirectUrl).contains("/orders/").contains("newOrder=true");

    // 6. Страница заказа
    mockMvc.perform(get(redirectUrl).session(session))
        .andExpect(status().isOk())
        .andExpect(view().name("order"))
        .andExpect(model().attribute("newOrder", true));

    // 7. Корзина пуста
    mockMvc.perform(get("/cart/items").session(session))
        .andExpect(model().attribute("total", 0L));

    // 8. Заказ в списке
    mockMvc.perform(get("/orders").session(session))
        .andExpect(status().isOk())
        .andExpect(model().attributeExists("orders"));
  }

  @Test
  void sessionsAreIsolated() throws Exception {
    MockHttpSession session1 = new MockHttpSession();
    MockHttpSession session2 = new MockHttpSession();

    // Сессия 1 добавляет товар
    mockMvc.perform(post("/items/" + testItem.getId())
            .param("action", "PLUS").session(session1))
        .andExpect(status().isOk());

    // Сессия 2 — корзина пуста
    mockMvc.perform(get("/cart/items").session(session2))
        .andExpect(model().attribute("total", 0L));

    // Сессия 1 — товар есть
    mockMvc.perform(get("/cart/items").session(session1))
        .andExpect(model().attribute("total", 1500L));
  }

  @Test
  void deleteFromCart() throws Exception {
    MockHttpSession session = new MockHttpSession();

    // Добавить
    mockMvc.perform(post("/items")
            .param("id", String.valueOf(testItem.getId()))
            .param("action", "PLUS").session(session))
        .andExpect(status().is3xxRedirection());

    // Удалить
    mockMvc.perform(post("/cart/items")
            .param("id", String.valueOf(testItem.getId()))
            .param("action", "DELETE").session(session))
        .andExpect(model().attribute("total", 0L));
  }
}
