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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.entity.CartItem;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.entity.User;
import ru.yandex.practicum.mymarket.repository.CartItemRepository;
import ru.yandex.practicum.mymarket.repository.ItemRepository;
import ru.yandex.practicum.mymarket.repository.UserRepository;
import ru.yandex.practicum.mymarket.service.PaymentClientService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the Sprint 8 invariant that each authenticated user has their own
 * cart, keyed by user ID — not shared via a session cookie.
 *
 * <p>Scenario:
 * <ol>
 *   <li>Alice logs in and adds an item to her cart.</li>
 *   <li>The {@code cart_items} table is queried directly to confirm the row
 *       was written with Alice's user ID.</li>
 *   <li>Bob logs in (separate session). His cart endpoint returns an empty cart.</li>
 *   <li>The DB is inspected again to confirm Bob has no rows in {@code cart_items}.</li>
 * </ol>
 *
 * <p>This test bypasses the production {@code SecurityWebFilterChain} (which
 * would require a running Keycloak), instead substituting a form-login chain
 * with CSRF disabled — same approach used by {@code FullFlowIntegrationTest}
 * and {@code AuthAccessControlTest}.
 */
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
class UserScopedCartTest {

  private static final Long ALICE_ID = 1L;
  private static final Long BOB_ID   = 2L;

  @MockBean
  PaymentClientService paymentClientService;

  @MockBean
  UserRepository userRepository;

  @TestConfiguration
  static class TestConfig {

    @Bean("springSecurityFilterChain")
    @Primary
    public SecurityWebFilterChain testSecurityFilterChain(ServerHttpSecurity http) {
      return http
          .authorizeExchange(ex -> ex
              .pathMatchers(org.springframework.http.HttpMethod.GET,
                  "/", "/items", "/items/*").permitAll()
              .pathMatchers("/login", "/logout").permitAll()
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

  @Autowired WebTestClient    webTestClient;
  @Autowired ItemRepository   itemRepository;
  @Autowired CartItemRepository cartItemRepository;

  private Item testItem;

  @BeforeEach
  void setUp() {
    when(userRepository.count()).thenReturn(Mono.just(2L));

    String alicePass = new BCryptPasswordEncoder().encode("alice123");
    User alice = new User("alice", alicePass);
    ReflectionTestUtils.setField(alice, "id", ALICE_ID);

    String bobPass = new BCryptPasswordEncoder().encode("bob123");
    User bob = new User("bob", bobPass);
    ReflectionTestUtils.setField(bob, "id", BOB_ID);

    when(userRepository.findByUsername("alice")).thenReturn(Mono.just(alice));
    when(userRepository.findByUsername("bob")).thenReturn(Mono.just(bob));

    when(paymentClientService.getBalance()).thenReturn(Mono.just(999_999L));
    when(paymentClientService.pay(anyLong(), anyLong())).thenReturn(Mono.just(999_999L));

    cartItemRepository.deleteAll()
        .then(itemRepository.deleteAll())
        .then(itemRepository.save(
                new Item("Тестовый товар", "Описание", "/img/t.jpg", 1000))
            .doOnNext(saved -> testItem = saved))
        .block();
  }

  @Test
  void aliceAndBob_cartsAreIsolatedByUserId() {
    // -------------------------------------------------------------------
    // Step 1 — Alice logs in and adds the item to her cart
    // -------------------------------------------------------------------
    WebTestClient aliceClient = login("alice", "alice123");

    aliceClient.post()
        .uri("/items/" + testItem.getId() + "?action=PLUS")
        .exchange()
        .expectStatus().isOk();

    // -------------------------------------------------------------------
    // Step 2 — DB shows the row belongs to Alice, not the session
    // -------------------------------------------------------------------
    var aliceCart = cartItemRepository.findByUserId(ALICE_ID).collectList().block();
    assertThat(aliceCart)
        .as("Alice's cart should contain the item she just added")
        .hasSize(1);
    assertThat(aliceCart.get(0).getItemId()).isEqualTo(testItem.getId());

    // -------------------------------------------------------------------
    // Step 3 — Bob logs in (different session) and sees an empty cart
    // -------------------------------------------------------------------
    WebTestClient bobClient = login("bob", "bob123");

    bobClient.get().uri("/cart/items")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html)
            .as("Bob's cart page must NOT show alice's item")
            .doesNotContain("Тестовый товар"));

    // -------------------------------------------------------------------
    // Step 4 — DB confirms Bob has no rows
    // -------------------------------------------------------------------
    var bobCart = cartItemRepository.findByUserId(BOB_ID).collectList().block();
    assertThat(bobCart)
        .as("Bob's cart should remain empty after alice added an item")
        .isEmpty();
  }

  @Test
  void bothUsers_addItem_eachKeepsOwnRow() {
    // Alice adds the item
    login("alice", "alice123").post()
        .uri("/items/" + testItem.getId() + "?action=PLUS")
        .exchange()
        .expectStatus().isOk();

    // Bob adds the same item
    login("bob", "bob123").post()
        .uri("/items/" + testItem.getId() + "?action=PLUS")
        .exchange()
        .expectStatus().isOk();

    // DB has two rows — one per user, each with count=1
    var allRows = cartItemRepository.findAll().collectList().block();
    assertThat(allRows).hasSize(2);

    var alice = (CartItem) allRows.stream()
        .filter(ci -> ci.getUserId().equals(ALICE_ID)).findFirst().orElseThrow();
    var bob = (CartItem) allRows.stream()
        .filter(ci -> ci.getUserId().equals(BOB_ID)).findFirst().orElseThrow();

    assertThat(alice.getItemId()).isEqualTo(testItem.getId());
    assertThat(alice.getCount()).isEqualTo(1);
    assertThat(bob.getItemId()).isEqualTo(testItem.getId());
    assertThat(bob.getCount()).isEqualTo(1);
  }

  // -----------------------------------------------------------------------
  // Helper — perform a real form-login POST and capture the SESSION cookie
  // -----------------------------------------------------------------------

  private WebTestClient login(String username, String password) {
    MultiValueMap<String, ResponseCookie> cookies = webTestClient
        .post().uri("/login")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(BodyInserters.fromFormData("username", username)
            .with("password", password))
        .exchange()
        .expectStatus().is3xxRedirection()
        .returnResult(String.class)
        .getResponseCookies();

    ResponseCookie session = cookies.getFirst("SESSION");
    assertThat(session)
        .as("SESSION cookie must be present after successful login for " + username)
        .isNotNull();

    return webTestClient.mutate()
        .defaultCookie("SESSION", session.getValue())
        .build();
  }
}
