package ru.yandex.practicum.mymarket.controller;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// No controller filter → loads all controllers → same context shared across all @WebFluxTest classes
@WebFluxTest
class CartControllerTest {

  @Autowired
  WebTestClient webTestClient;

  @MockitoBean
  ItemService itemService;

  @MockitoBean
  CartService cartService;

  @MockitoBean
  OrderService orderService;

  @Test
  void getCart_returnsCartPage() {
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
  void getCart_emptyCart_totalSectionHidden() {
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
  void postCart_delete_callsHandleActionAndReturnsCart() {
    when(cartService.handleAction(any(), eq(1L), eq(CartAction.DELETE)))
        .thenReturn(Mono.empty());
    when(cartService.getCartItemDtos(any())).thenReturn(Flux.empty());
    when(cartService.getCartTotal(any())).thenReturn(Mono.just(0L));

    // Pass params as query string — @RequestParam reads from both query string and form body;
    // using query string avoids form-body decoding differences in the @WebFluxTest mock context.
    webTestClient.post().uri("/cart/items?id=1&action=DELETE")
        .exchange()
        .expectStatus().isOk();

    verify(cartService).handleAction(any(), eq(1L), eq(CartAction.DELETE));
  }

  @Test
  void postCart_plus_callsHandleAction() {
    when(cartService.handleAction(any(), eq(3L), eq(CartAction.PLUS)))
        .thenReturn(Mono.empty());
    when(cartService.getCartItemDtos(any())).thenReturn(Flux.empty());
    when(cartService.getCartTotal(any())).thenReturn(Mono.just(0L));

    webTestClient.post().uri("/cart/items?id=3&action=PLUS")
        .exchange()
        .expectStatus().isOk();

    verify(cartService).handleAction(any(), eq(3L), eq(CartAction.PLUS));
  }
}
