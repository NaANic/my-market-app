package ru.yandex.practicum.mymarket.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Full-stack integration test: real H2 database, mocked Redis, mocked payment-service.
 *
 * <h3>Why allow-bean-definition-overriding is needed</h3>
 * Spring Boot 3 / Spring 6 disables bean definition overriding by default
 * ({@code spring.main.allow-bean-definition-overriding=false}). Our
 * {@link TestConfig} declares {@code @Bean} methods with the same names as
 * {@code RedisConfig} ({@code itemRedisTemplate}, {@code stringRedisTemplate}).
 * Without the override flag Spring refuses to register the second definition
 * and throws {@code BeanDefinitionOverrideException}.
 *
 * <p>Enabling overriding lets the {@code @Primary} test beans replace the
 * production ones so that {@code ItemService} receives pre-stubbed mocks
 * (permanent cache miss → H2 fallback) without any real Redis connection.
 *
 * <h3>Why the factory mock is also needed</h3>
 * {@code RedisConfig} is still instantiated and its {@code @Bean} methods
 * are called (they are simply overridden in the registry afterwards). Those
 * methods require a {@code ReactiveRedisConnectionFactory} parameter, which
 * Lettuce would otherwise satisfy by opening a real TCP socket. The mock
 * factory provides a safe no-op alternative.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        // Allow TestConfig beans to override same-named beans in RedisConfig.
        // Required in Spring Boot 3 which disables overriding by default.
        "spring.main.allow-bean-definition-overriding=true",
        // Exclude Lettuce auto-configuration so it never tries to connect.
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
    }
)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class FullFlowIntegrationTest {

  // -----------------------------------------------------------------------
  // PaymentClientService mock — no network calls to payment-service
  // -----------------------------------------------------------------------

  @MockBean
  PaymentClientService paymentClientService;

  // -----------------------------------------------------------------------
  // Test configuration
  // -----------------------------------------------------------------------

  @TestConfiguration
  static class TestConfig {

    /**
     * Mock factory so that {@code RedisConfig}'s {@code @Bean} methods
     * receive a non-null parameter. The factory is only called at
     * subscription time (lazily), so returning null from its methods
     * is safe during context startup.
     */
    @Bean
    @Primary
    ReactiveRedisConnectionFactory reactiveRedisConnectionFactory() {
      return mock(ReactiveRedisConnectionFactory.class);
    }

    /**
     * Overrides {@code RedisConfig.itemRedisTemplate()}.
     * All reads return {@code Mono.empty()} (permanent miss → H2 read).
     */
    @Bean
    @Primary
    @SuppressWarnings({"unchecked", "rawtypes"})
    ReactiveRedisTemplate<String, Item> itemRedisTemplate() {
      ReactiveRedisTemplate<String, Item> tpl = mock(ReactiveRedisTemplate.class);
      ReactiveValueOperations<String, Item> ops = mock(ReactiveValueOperations.class);
      when(tpl.opsForValue()).thenReturn(ops);
      when(ops.get(anyString())).thenReturn(Mono.empty());
      when(ops.set(any(), any(), any())).thenReturn(Mono.just(true));
      return tpl;
    }

    /**
     * Overrides {@code RedisConfig.stringRedisTemplate()}.
     * All reads return {@code Mono.empty()} (permanent miss → H2 read).
     */
    @Bean
    @Primary
    @SuppressWarnings({"unchecked", "rawtypes"})
    ReactiveRedisTemplate<String, String> stringRedisTemplate() {
      ReactiveRedisTemplate<String, String> tpl = mock(ReactiveRedisTemplate.class);
      ReactiveValueOperations<String, String> ops = mock(ReactiveValueOperations.class);
      when(tpl.opsForValue()).thenReturn(ops);
      when(ops.get(anyString())).thenReturn(Mono.empty());
      when(ops.set(anyString(), anyString(), any())).thenReturn(Mono.just(true));
      return tpl;
    }
  }

  // -----------------------------------------------------------------------
  // Fixtures
  // -----------------------------------------------------------------------

  @Autowired WebTestClient webTestClient;
  @Autowired ItemRepository itemRepository;
  @Autowired CartItemRepository cartItemRepository;
  @Autowired OrderRepository orderRepository;
  @Autowired OrderItemRepository orderItemRepository;

  private Item testItem;

  @BeforeEach
  void setUp() {
    when(paymentClientService.pay(anyLong(), anyLong())).thenReturn(Mono.just(999_999L));
    when(paymentClientService.getBalance()).thenReturn(Mono.just(999_999L));

    orderItemRepository.deleteAll()
        .then(orderRepository.deleteAll())
        .then(cartItemRepository.deleteAll())
        .then(itemRepository.deleteAll())
        .then(itemRepository.save(
                new Item("Тестовый мяч", "Мяч для тестов", "/img/ball.jpg", 1500))
            .doOnNext(saved -> testItem = saved))
        .block();
  }

  // -----------------------------------------------------------------------
  // Session helper
  // -----------------------------------------------------------------------

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

  // -----------------------------------------------------------------------
  // Tests
  // -----------------------------------------------------------------------

  @Test
  void fullPurchaseFlow() {
    WebTestClient client = startSession();

    client.post().uri("/items/" + testItem.getId() + "?action=PLUS")
        .exchange().expectStatus().isOk();
    client.post().uri("/items/" + testItem.getId() + "?action=PLUS")
        .exchange().expectStatus().isOk();

    client.get().uri("/cart/items")
        .exchange().expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html).contains("Итого:"));

    client.post().uri("/buy")
        .exchange().expectStatus().is3xxRedirection();

    client.get().uri("/cart/items")
        .exchange().expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html).doesNotContain("Итого:"));

    client.get().uri("/orders")
        .exchange().expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html).contains("Заказ №"));
  }

  @Test
  void deleteFromCart_removesItem() {
    WebTestClient client = startSession();

    client.post().uri("/items?id=" + testItem.getId() + "&action=PLUS")
        .exchange().expectStatus().is3xxRedirection();

    client.post().uri("/cart/items?id=" + testItem.getId() + "&action=DELETE")
        .exchange().expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html).doesNotContain("Итого:"));
  }
}
