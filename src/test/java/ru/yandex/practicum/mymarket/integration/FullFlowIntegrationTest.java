package ru.yandex.practicum.mymarket.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.MultiValueMap;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class FullFlowIntegrationTest {

  @Autowired
  WebTestClient webTestClient;

  @Autowired
  ItemRepository itemRepository;

  @Autowired
  CartItemRepository cartItemRepository;

  @Autowired
  OrderRepository orderRepository;

  @Autowired
  OrderItemRepository orderItemRepository;

  private Item testItem;

  @BeforeEach
  void setUp() {
    // Delete in FK-safe order: order_items → customer_orders → cart_items → items
    orderItemRepository.deleteAll()
        .then(orderRepository.deleteAll())
        .then(cartItemRepository.deleteAll())
        .then(itemRepository.deleteAll())
        .then(itemRepository.save(new Item("Тестовый мяч", "Мяч для тестов", "/img/ball.jpg", 1500))
            .doOnNext(saved -> testItem = saved))
        .block();
  }

  /**
   * Establishes a new server-side session by hitting GET /items and extracts the SESSION cookie.
   * Returns a WebTestClient pre-configured with that cookie for all subsequent requests.
   */
  private WebTestClient startSession() {
    MultiValueMap<String, ResponseCookie> cookies = webTestClient.get().uri("/items")
        .exchange()
        .expectStatus().isOk()
        .returnResult(String.class)
        .getResponseCookies();

    ResponseCookie sessionCookie = cookies.getFirst("SESSION");
    assertThat(sessionCookie).as("SESSION cookie must be present after GET /items").isNotNull();

    return webTestClient.mutate()
        .defaultCookie("SESSION", sessionCookie.getValue())
        .build();
  }

  @Test
  void fullPurchaseFlow() {
    WebTestClient client = startSession();

    // 1. Add item twice (params in query string — works in both real server and test contexts)
    client.post().uri("/items/" + testItem.getId() + "?action=PLUS")
        .exchange()
        .expectStatus().isOk();

    client.post().uri("/items/" + testItem.getId() + "?action=PLUS")
        .exchange()
        .expectStatus().isOk();

    // 2. Cart shows total 2 × 1500 = 3000
    client.get().uri("/cart/items")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html).contains("Итого: 3000 руб."));

    // 3. Decrease by one: 1 × 1500 = 1500
    client.post().uri("/cart/items?id=" + testItem.getId() + "&action=MINUS")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html).contains("Итого: 1500 руб."));

    // 4. Buy
    String redirectLocation = client.post().uri("/buy")
        .exchange()
        .expectStatus().is3xxRedirection()
        .returnResult(String.class)
        .getResponseHeaders()
        .getFirst("Location");

    assertThat(redirectLocation)
        .isNotNull()
        .contains("/orders/")
        .contains("newOrder=true");

    // 5. Order page shows congratulations
    client.get().uri(redirectLocation)
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> {
          assertThat(html).contains("Поздравляем");
          assertThat(html).contains("Заказ №");
        });

    // 6. Cart is empty after purchase
    client.get().uri("/cart/items")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html).doesNotContain("Итого:"));

    // 7. Order appears in orders list
    client.get().uri("/orders")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html).contains("Заказ №"));
  }

  @Test
  void sessionsAreIsolated() {
    WebTestClient session1 = startSession();
    WebTestClient session2 = startSession();

    // Session 1 adds item
    session1.post().uri("/items/" + testItem.getId() + "?action=PLUS")
        .exchange()
        .expectStatus().isOk();

    // Session 2 — cart should be empty
    session2.get().uri("/cart/items")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html).doesNotContain("Итого:"));

    // Session 1 — cart should have item
    session1.get().uri("/cart/items")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html).contains("Итого: 1500 руб."));
  }

  @Test
  void deleteFromCart_removesItem() {
    WebTestClient client = startSession();

    // Add via items list (results in redirect — follow manually)
    client.post().uri("/items?id=" + testItem.getId() + "&action=PLUS")
        .exchange()
        .expectStatus().is3xxRedirection();

    // Delete from cart
    client.post().uri("/cart/items?id=" + testItem.getId() + "&action=DELETE")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html).doesNotContain("Итого:"));
  }
}
