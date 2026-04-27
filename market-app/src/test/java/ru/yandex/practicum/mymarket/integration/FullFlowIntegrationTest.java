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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.entity.User;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;
import ru.yandex.practicum.mymarket.repository.UserRepository;
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
            "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration," +
            "org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration," +
            "org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration"
    }
)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class FullFlowIntegrationTest {

  @MockBean
  PaymentClientService paymentClientService;

  @MockBean
  UserRepository userRepository;

  @TestConfiguration
  static class TestConfig {

    /**
     * Overrides the real SecurityWebFilterChain for integration tests.
     * CSRF is disabled so POST requests don't need a token — the real
     * session cookie (from form login) still provides authentication continuity.
     */
    @Bean("springSecurityFilterChain")
    @Primary
    public SecurityWebFilterChain testSecurityFilterChain(ServerHttpSecurity http) {
      return http
          .authorizeExchange(ex -> ex.anyExchange().authenticated())
          .formLogin(Customizer.withDefaults())
          .csrf(ServerHttpSecurity.CsrfSpec::disable)
          .build();
    }

    @Bean
    @Primary
    ReactiveRedisConnectionFactory reactiveRedisConnectionFactory() {
      return mock(ReactiveRedisConnectionFactory.class);
    }

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
    when(userRepository.count()).thenReturn(Mono.just(2L));
    String encoded = new BCryptPasswordEncoder().encode("alice123");
    User alice = new User("alice", encoded);
    when(userRepository.findByUsername("alice")).thenReturn(Mono.just(alice));
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

  private WebTestClient loginAndGetClient() {
    // POST /login directly — Spring creates a SESSION cookie on success
    MultiValueMap<String, ResponseCookie> cookiesFromPost = webTestClient
        .post().uri("/login")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(BodyInserters.fromFormData("username", "alice")
            .with("password", "alice123"))
        .exchange()
        .expectStatus().is3xxRedirection()
        .returnResult(String.class)
        .getResponseCookies();

    ResponseCookie session = cookiesFromPost.getFirst("SESSION");
    assertThat(session)
        .as("SESSION cookie must be present after successful login")
        .isNotNull();

    return webTestClient.mutate()
        .defaultCookie("SESSION", session.getValue())
        .build();
  }

  @Test
  void fullPurchaseFlow() {
    WebTestClient client = loginAndGetClient();

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
    WebTestClient client = loginAndGetClient();

    client.post().uri("/items?id=" + testItem.getId() + "&action=PLUS")
        .exchange().expectStatus().is3xxRedirection();

    client.post().uri("/cart/items?id=" + testItem.getId() + "&action=DELETE")
        .exchange().expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html).doesNotContain("Итого:"));
  }
}
