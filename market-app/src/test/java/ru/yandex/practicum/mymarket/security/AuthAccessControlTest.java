package ru.yandex.practicum.mymarket.security;

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
import ru.yandex.practicum.mymarket.repository.UserRepository;
import ru.yandex.practicum.mymarket.service.PaymentClientService;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the Sprint 8 access-control rules:
 * <ul>
 *   <li>Anonymous users CAN browse the catalog ({@code GET /items}, {@code GET /items/{id}}).</li>
 *   <li>Anonymous users are redirected to the login page when accessing
 *       cart pages or making cart-mutating requests.</li>
 *   <li>Authenticated users can access all the above endpoints.</li>
 * </ul>
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "spring.security.oauth2.client.registration.keycloak.client-id=disabled",
        "spring.security.oauth2.client.registration.keycloak.client-secret=disabled",
        "spring.security.oauth2.client.registration.keycloak.authorization-grant-type=client_credentials",
        "spring.security.oauth2.client.provider.keycloak.issuer-uri=http://localhost:9999/realms/test",
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration," +
            "org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration," +
            "org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration"
    }
)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class AuthAccessControlTest {

  @MockBean
  PaymentClientService paymentClientService;

  @MockBean
  UserRepository userRepository;

  @TestConfiguration
  static class TestConfig {

    /** Override security to disable CSRF for simpler test calls. */
    @Bean("springSecurityFilterChain")
    @Primary
    public SecurityWebFilterChain testSecurityFilterChain(ServerHttpSecurity http) {
      return http
          .authorizeExchange(ex -> ex
              .pathMatchers(org.springframework.http.HttpMethod.GET,
                  "/", "/items", "/items/*").permitAll()
              .pathMatchers("/login", "/logout", "/css/**", "/images/**",
                  "/webjars/**", "/actuator/health").permitAll()
              .anyExchange().authenticated()
          )
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

  private Item testItem;

  @BeforeEach
  void setUp() {
    when(userRepository.count()).thenReturn(Mono.just(2L));
    String encoded = new BCryptPasswordEncoder().encode("alice123");
    User alice = new User("alice", encoded);
    ReflectionTestUtils.setField(alice, "id", 1L);
    when(userRepository.findByUsername("alice"))
        .thenReturn(Mono.just(alice));

    when(paymentClientService.getBalance()).thenReturn(Mono.just(999_999L));
    when(paymentClientService.pay(anyLong(), anyLong())).thenReturn(Mono.just(999_999L));

    cartItemRepository.deleteAll()
        .then(itemRepository.deleteAll())
        .then(itemRepository.save(
                new Item("Тестовый товар", "Описание", "/img/test.jpg", 1000))
            .doOnNext(saved -> testItem = saved))
        .block();
  }

  // -----------------------------------------------------------------------
  // Anonymous access — public endpoints
  // -----------------------------------------------------------------------

  @Test
  void anonymous_canAccessCatalog() {
    webTestClient.get().uri("/items")
        .exchange()
        .expectStatus().isOk();
  }

  @Test
  void anonymous_canAccessItemDetail() {
    webTestClient.get().uri("/items/" + testItem.getId())
        .exchange()
        .expectStatus().isOk();
  }

  // -----------------------------------------------------------------------
  // Anonymous access — protected endpoints redirect to /login
  // -----------------------------------------------------------------------

  @Test
  void anonymous_cartPage_redirectsToLogin() {
    webTestClient.get().uri("/cart/items")
        .exchange()
        .expectStatus().is3xxRedirection()
        .expectHeader().value("Location", loc ->
            assertThat(loc).contains("/login"));
  }

  @Test
  void anonymous_ordersPage_redirectsToLogin() {
    webTestClient.get().uri("/orders")
        .exchange()
        .expectStatus().is3xxRedirection()
        .expectHeader().value("Location", loc ->
            assertThat(loc).contains("/login"));
  }

  // -----------------------------------------------------------------------
  // Authenticated access — same endpoints succeed
  // -----------------------------------------------------------------------

  @Test
  void authenticated_canAccessCart() {
    WebTestClient client = loginAsAlice();

    client.get().uri("/cart/items")
        .exchange()
        .expectStatus().isOk();
  }

  @Test
  void authenticated_canAccessOrders() {
    WebTestClient client = loginAsAlice();

    client.get().uri("/orders")
        .exchange()
        .expectStatus().isOk();
  }

  // -----------------------------------------------------------------------
  // Helper — perform real form login and capture session cookie
  // -----------------------------------------------------------------------

  private WebTestClient loginAsAlice() {
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
}
