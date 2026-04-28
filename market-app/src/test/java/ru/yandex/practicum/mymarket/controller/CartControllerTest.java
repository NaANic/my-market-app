package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartAction;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.ItemService;
import ru.yandex.practicum.mymarket.service.OrderService;
import ru.yandex.practicum.mymarket.service.PaymentClientService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.context.annotation.Import;
import ru.yandex.practicum.mymarket.config.TestSecurityConfig;
import ru.yandex.practicum.mymarket.repository.UserRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import ru.yandex.practicum.mymarket.service.CurrentUserService;
import reactor.core.publisher.Mono;


// No controller filter → loads all controllers → same context shared across all @WebFluxTest classes
@WebFluxTest
@Import(TestSecurityConfig.class)
class CartControllerTest {

  @Autowired
  WebTestClient webTestClient;

  @MockitoBean
  ItemService itemService;

  @MockitoBean
  CartService cartService;

  @MockitoBean
  OrderService orderService;

  @MockitoBean
  UserRepository userRepository;

  @MockitoBean
  CurrentUserService currentUserService;

  /**
   * CartController now injects PaymentClientService to call getBalance()
   * when building the cart page model. Without this bean in the
   * @WebFluxTest context, Spring cannot wire CartController and the entire
   * shared controller context fails to load.
   */
  @MockitoBean
  PaymentClientService paymentClientService;

  /**
   * Stub getBalance() with a large value before every test so that
   * canAfford = true for non-empty carts and the Купить button is enabled.
   * Tests that specifically need a different balance override this stub inline.
   */
  @BeforeEach
  void stubBalance() {
    when(paymentClientService.getBalance()).thenReturn(Mono.just(999_999L));
  }

  @Test
  @WithMockUser
  void getCart_returnsCartPage() {
    when(currentUserService.getCurrentUserId()).thenReturn(Mono.just(1L));
    when(cartService.getCartItemDtos(any())).thenReturn(Flux.just(
        new ItemDto(1L, "Мяч", "Desc", "/img/t.jpg", 2500, 2)
    ));
    when(cartService.getCartTotal(any())).thenReturn(Mono.just(5000L));

    webTestClient.get().uri("/cart/items")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> {
          assertThat(html).contains("Мяч");
          assertThat(html).contains("Итого: 5000 руб.");
        });
  }

  @Test
  @WithMockUser
  void getCart_emptyCart_totalSectionHidden() {
    when(currentUserService.getCurrentUserId()).thenReturn(Mono.just(1L));
    when(cartService.getCartItemDtos(any())).thenReturn(Flux.empty());
    when(cartService.getCartTotal(any())).thenReturn(Mono.just(0L));

    // cart.html hides the "Итого:" row when items list is empty (th:if="${!items.isEmpty()}")
    webTestClient.get().uri("/cart/items")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html).doesNotContain("Итого:"));
  }

  @Test
  @WithMockUser
  void getCart_insufficientBalance_buttonDisabled() {
    when(currentUserService.getCurrentUserId()).thenReturn(Mono.just(1L));
    when(cartService.getCartItemDtos(any())).thenReturn(Flux.just(
        new ItemDto(1L, "Мяч", "Desc", "/img/t.jpg", 2500, 2)
    ));
    when(cartService.getCartTotal(any())).thenReturn(Mono.just(5000L));
    // Balance below total → canAfford = false → button rendered with disabled attribute
    when(paymentClientService.getBalance()).thenReturn(Mono.just(100L));

    webTestClient.get().uri("/cart/items")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html).contains("disabled"));
  }

  @Test
  @WithMockUser
  void getCart_paymentServiceUnavailable_buttonDisabled() {
    when(currentUserService.getCurrentUserId()).thenReturn(Mono.just(1L));
    when(cartService.getCartItemDtos(any())).thenReturn(Flux.just(
        new ItemDto(1L, "Мяч", "Desc", "/img/t.jpg", 2500, 2)
    ));
    when(cartService.getCartTotal(any())).thenReturn(Mono.just(5000L));
    // Simulate payment service down → onErrorReturn(0L) → canAfford = false
    when(paymentClientService.getBalance()).thenReturn(Mono.error(new RuntimeException("down")));

    webTestClient.get().uri("/cart/items")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html).contains("disabled"));
  }

  @Test
  @WithMockUser
  void postCart_delete_callsHandleActionAndReturnsCart() {
    when(currentUserService.getCurrentUserId()).thenReturn(Mono.just(1L));
    when(cartService.handleAction(any(), eq(1L), eq(CartAction.DELETE)))
        .thenReturn(Mono.empty());
    when(cartService.getCartItemDtos(any())).thenReturn(Flux.empty());
    when(cartService.getCartTotal(any())).thenReturn(Mono.just(0L));

    webTestClient
        .mutateWith(SecurityMockServerConfigurers.csrf())
        .post().uri("/cart/items?id=1&action=DELETE")
        .exchange()
        .expectStatus().isOk();

    verify(cartService).handleAction(any(), eq(1L), eq(CartAction.DELETE));
  }

  @Test
  @WithMockUser
  void postCart_plus_callsHandleAction() {
    when(currentUserService.getCurrentUserId()).thenReturn(Mono.just(1L));
    when(cartService.handleAction(any(), eq(3L), eq(CartAction.PLUS)))
        .thenReturn(Mono.empty());
    when(cartService.getCartItemDtos(any())).thenReturn(Flux.empty());
    when(cartService.getCartTotal(any())).thenReturn(Mono.just(0L));

    webTestClient
        .mutateWith(SecurityMockServerConfigurers.csrf())
        .post().uri("/cart/items?id=3&action=PLUS")
        .exchange()
        .expectStatus().isOk();

    verify(cartService).handleAction(any(), eq(3L), eq(CartAction.PLUS));
  }
}
