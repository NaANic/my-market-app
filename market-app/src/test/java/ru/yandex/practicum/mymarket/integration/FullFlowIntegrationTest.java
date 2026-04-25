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

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration"
    }
)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class FullFlowIntegrationTest {

  @MockBean
  PaymentClientService paymentClientService;

  @TestConfiguration
  static class TestConfig {

    @Bean
    @Primary
    ReactiveRedisConnectionFactory reactiveRedisConnectionFactory() {
      return mock(ReactiveRedisConnectionFactory.class);
    }

    /**
     * Overrides {@code RedisConfig.itemCache()}.
     * Bean name must match the name declared in {@code RedisConfig} ("itemCache").
     */
    @Bean("itemCache")
    @Primary
    @SuppressWarnings({"unchecked", "rawtypes"})
    ReactiveRedisTemplate<String, Item> itemCache() {
      ReactiveRedisTemplate<String, Item> tpl = mock(ReactiveRedisTemplate.class);
      ReactiveValueOperations<String, Item> ops = mock(ReactiveValueOperations.class);
      when(tpl.opsForValue()).thenReturn(ops);
      when(ops.get(anyString())).thenReturn(Mono.empty());
      when(ops.set(any(), any(), any())).thenReturn(Mono.just(true));
      return tpl;
    }

    /**
     * Overrides {@code RedisConfig.stringCache()}.
     * Bean name must match the name declared in {@code RedisConfig} ("stringCache").
     */
    @Bean("stringCache")
    @Primary
    @SuppressWarnings({"unchecked", "rawtypes"})
    ReactiveRedisTemplate<String, String> stringCache() {
      ReactiveRedisTemplate<String, String> tpl = mock(ReactiveRedisTemplate.class);
      ReactiveValueOperations<String, String> ops = mock(ReactiveValueOperations.class);
      when(tpl.opsForValue()).thenReturn(ops);
      when(ops.get(anyString())).thenReturn(Mono.empty());
      when(ops.set(anyString(), anyString(), any())).thenReturn(Mono.just(true));
      return tpl;
    }
  }

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
