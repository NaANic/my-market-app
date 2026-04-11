package ru.yandex.practicum.mymarket.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;
import ru.yandex.practicum.mymarket.service.PaymentClientService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class FullFlowIntegrationTest {

  // -----------------------------------------------------------------------
  // Test configuration — replaces infrastructure beans that are unavailable
  // in the unit-test environment (no live Redis, no live payment-service).
  // -----------------------------------------------------------------------

  @TestConfiguration
  static class TestConfig {

    /**
     * Replaces the real {@link ReactiveRedisConnectionFactory} (which would
     * try to connect to a Redis server) with a no-op mock.
     *
     * <p>{@code @Primary} ensures this bean wins over the auto-configured one.
     * The {@code application-test.properties} also sets
     * {@code spring.data.redis.repositories.enabled=false} and a dummy host
     * so that Lettuce does not attempt a connection during context startup.
     */
    @Bean
    @Primary
    ReactiveRedisConnectionFactory reactiveRedisConnectionFactory() {
      return mock(ReactiveRedisConnectionFactory.class);
    }

    /**
     * Replaces the real {@link PaymentClientService} (which requires a live
     * payment-service at {@code payment.service.url}) with a mock that always
     * returns a successful payment with a large remaining balance.
     *
     * <p>Integration tests exercise the full web → service → repository → DB
     * path; payment behaviour is covered by unit tests.
     */
    @Bean
    @Primary
    PaymentClientService paymentClientService() {
      PaymentClientService mock = mock(PaymentClientService.class);
      when(mock.pay(anyLong(), anyLong())).thenReturn(Mono.just(999_999L));
      when(mock.getBalance()).thenReturn(Mono.just(999_999L));
      return mock;
    }
  }

  // -----------------------------------------------------------------------
  // Test body (unchanged from original)
  // -----------------------------------------------------------------------

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
        .then(itemRepository.save(
                new Item("Тестовый мяч", "Мяч для тестов", "/img/ball.jpg", 1500))
            .doOnNext(saved -> testItem = saved))
        .block();
  }

  /**
   * Establishes a new server-side session by hitting GET /items and extracts
   * the SESSION cookie. Returns a {@link WebTestClient} pre-configured with
   * that cookie for all subsequent requests.
   */
  private WebTestClient startSession() {
    MultiValueMap<String, ResponseCookie> cookies = webTestClient.get().uri("/items")
        .exchange()
        .expectStatus().isOk()
        .returnResult(String.class)
        .getResponseCookies();

    ResponseCookie sessionCookie = cookies.getFirst("SESSION");
    assertThat(sessionCookie)
        .as("SESSION cookie must be present after GET /items")
        .isNotNull();

    return webTestClient.mutate()
        .defaultCookie("SESSION", sessionCookie.getValue())
        .build();
  }

  @Test
  void fullPurchaseFlow() {
    WebTestClient client = startSession();

    // 1. Add item twice
    client.post().uri("/items/" + testItem.getId() + "?action=PLUS")
        .exchange()
        .expectStatus().isOk();

    client.post().uri("/items/" + testItem.getId() + "?action=PLUS")
        .exchange()
        .expectStatus().isOk();

    // 2. Verify cart shows 2 items
    client.get().uri("/cart/items")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html).contains("Итого:"));

    // 3. Check out
    client.post().uri("/buy")
        .exchange()
        .expectStatus().is3xxRedirection();

    // 4. Cart must be empty after purchase
    client.get().uri("/cart/items")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html).doesNotContain("Итого:"));

    // 5. Order must appear in order history
    client.get().uri("/orders")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html).contains("Заказ №"));
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
