package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.ItemService;
import ru.yandex.practicum.mymarket.service.OrderService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// No controller filter → loads all controllers → same context shared across all @WebFluxTest classes
@WebFluxTest
class OrderControllerTest {

  @Autowired
  WebTestClient webTestClient;

  @MockitoBean
  ItemService itemService;

  @MockitoBean
  CartService cartService;

  @MockitoBean
  OrderService orderService;

  @Test
  void getOrders_rendersOrdersList() {
    OrderDto order = new OrderDto(1L,
        List.of(new ItemDto(10L, "Мяч", null, null, 1500, 2)),
        3000L);
    when(orderService.getOrders(any())).thenReturn(Flux.just(order));

    webTestClient.get().uri("/orders")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html).contains("Заказ №1"));
  }

  @Test
  void getOrders_emptyList_rendersPageWithoutOrders() {
    when(orderService.getOrders(any())).thenReturn(Flux.empty());

    webTestClient.get().uri("/orders")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html).doesNotContain("Заказ №"));
  }

  @Test
  void getOrder_rendersOrderPage() {
    OrderDto order = new OrderDto(5L,
        List.of(new ItemDto(10L, "Ракетка", null, null, 6100, 1)),
        6100L);
    when(orderService.getOrder(5L)).thenReturn(Mono.just(order));

    webTestClient.get().uri("/orders/5")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> {
          assertThat(html).contains("Заказ №5");
          assertThat(html).contains("Ракетка");
          assertThat(html).doesNotContain("Поздравляем");
        });
  }

  @Test
  void getOrder_withNewOrderFlag_showsCongrats() {
    OrderDto order = new OrderDto(7L, List.of(), 0L);
    when(orderService.getOrder(7L)).thenReturn(Mono.just(order));

    webTestClient.get().uri("/orders/7?newOrder=true")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html).contains("Поздравляем"));
  }

  @Test
  void buy_redirectsToNewOrder() {
    when(orderService.createOrder(any())).thenReturn(Mono.just(42L));

    webTestClient.post().uri("/buy")
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(BodyInserters.fromFormData("dummy", "value"))
        .exchange()
        .expectStatus().is3xxRedirection()
        .expectHeader().value("Location", loc ->
            assertThat(loc).contains("/orders/42").contains("newOrder=true"));
  }
}
